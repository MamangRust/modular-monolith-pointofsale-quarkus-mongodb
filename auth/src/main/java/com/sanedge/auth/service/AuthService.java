package com.sanedge.auth.service;

import java.time.Instant;
import org.bson.types.ObjectId;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

import com.sanedge.auth.entity.Outbox;
import com.sanedge.auth.entity.RefreshToken;
import com.sanedge.auth.entity.ResetToken;
import com.sanedge.auth.repository.OutboxRepository;
import com.sanedge.auth.repository.RefreshTokenRepository;
import com.sanedge.auth.repository.ResetTokenRepository;
import com.sanedge.common.adapter.model.CreateUserCmd;
import com.sanedge.common.adapter.model.UpdateUserCmd;
import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.role.RoleCommandPort;
import com.sanedge.common.adapter.role.RoleQueryPort;
import com.sanedge.common.adapter.user.UserCommandPort;
import com.sanedge.common.adapter.user.UserQueryPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.utils.JwtUtil;
import com.sanedge.common.utils.PasswordUtil;
import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pb.user.User.UserResponse;
import com.sanedge.auth.domain.requests.RegisterRequest;
import com.sanedge.auth.domain.requests.ResetPasswordRequest;

@ApplicationScoped
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Inject
    UserQueryPort userQueryPort;

    @Inject
    UserCommandPort userCommandPort;

    @Inject
    RoleQueryPort roleQueryPort;

    @Inject
    RoleCommandPort roleCommandPort;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    ResetTokenRepository resetTokenRepository;

    @Inject
    RedisService redisService;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    JwtUtil jwtUtil;

    @Inject
    PasswordUtil passwordUtil;

    @Inject
    TracingMetrics tracingMetrics;

    public Uni<UserResponse> register(RegisterRequest req) {
        Attributes attrs = Attributes.builder()
                .put("user.email", req.getEmail())
                .build();

        return runTraced("registerUser", "register", attrs, () -> {
            log.info("Attempting to register user with email: {}", req.getEmail());
            return userQueryPort
                    .findByEmail(req.getEmail())
                    .chain(existing -> {
                        if (existing != null) {
                            return Uni.createFrom()
                                    .failure(new RuntimeException("User with this email already exists"));
                        }

                        CreateUserCmd createCmd = new CreateUserCmd(
                                req.getFirstName(),
                                req.getLastName(),
                                req.getEmail(),
                                req.getPassword(),
                                req.getPassword());

                        return userCommandPort.create(createCmd);
                    })
                    .chain(user -> {
                        String verificationCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

                        return redisService.setWithExpirationReactive("verification:" + req.getEmail(), verificationCode, 900)
                                .chain(() -> redisService.setWithExpirationReactive(
                                        "verification_code:" + verificationCode,
                                        req.getEmail(), 900))
                                .chain(() -> sendWelcomeEmail(user, verificationCode))
                                .chain(() -> {
                                    return roleQueryPort
                                            .findByName("ROLE_USER")
                                            .onFailure().recoverWithItem(err -> {
                                                log.warn("Failed to find ROLE_USER, skipping role assignment: {}",
                                                        err.getMessage());
                                                return null;
                                            })
                                            .chain(role -> {
                                                if (role == null
                                                        || role.id() == null
                                                        || role.id().isEmpty()) {
                                                    return Uni.createFrom().item(user);
                                                }
                                                return roleCommandPort.assignRoleToUser(user.id(), role.id())
                                                        .replaceWith(user);
                                            });
                                });
                    })
                    .map(AuthService::toPbUser)
                    .invoke(user -> log.info("Successfully registered user: {}", user.getEmail()));
        });
    }

    public Uni<String[]> login(String email, String password) {
        Attributes attrs = Attributes.builder()
                .put("user.email", email)
                .build();

        return runTraced("loginUser", "login", attrs, () -> {
            log.info("Attempting login for email: {}", email);
            String failedAttemptsKey = "failed_login:" + email;
            String lockKey = "account_locked:" + email;

            return redisService.existsReactive(lockKey)
                    .chain(locked -> {
                        if (locked) {
                            return Uni.createFrom()
                                    .failure(new RuntimeException("Account is locked due to too many failed attempts"));
                        }
                        return userQueryPort.verifyPassword(email, password);
                    })
                    .chain(verifyRes -> {
                        if (!verifyRes.valid()) {
                            return handleFailedLogin(email, failedAttemptsKey, lockKey);
                        }

                        User user = verifyRes.user();

                        String accessToken = jwtUtil.generateToken(user.email(),
                                Collections.singletonList("ROLE_USER"),
                                user.id());
                        String refreshTokenStr = jwtUtil.generateRefreshToken(user.email(), user.id());

                        RefreshToken rt = new RefreshToken();
                        rt.setUserId(new ObjectId(user.id()));
                        rt.setToken(refreshTokenStr);
                        rt.setExpiration(Instant.now().plusMillis(jwtUtil.getRefreshExpirationMs()));

                        return redisService.deleteReactive(failedAttemptsKey)
                                .chain(() -> refreshTokenRepository.deleteByUserId(new ObjectId(user.id())))
                                .chain(() -> refreshTokenRepository.persist(rt))
                                .map(v -> new String[] { accessToken, refreshTokenStr });
                    })
                    .invoke(tokens -> log.info("Successfully logged in user: {}", email));
        });
    }

    public Uni<String[]> refresh(String refreshTokenStr) {
        Attributes attrs = Attributes.builder()
                .put("operation", "refresh")
                .build();

        return runTraced("refreshToken", "refresh", attrs, () -> {
            log.info("Attempting to refresh access token");
            if (!jwtUtil.validateToken(refreshTokenStr)) {
                return Uni.createFrom().failure(new RuntimeException("Invalid or expired refresh token"));
            }

            return refreshTokenRepository.findByToken(refreshTokenStr)
                    .chain(rt -> {
                        if (rt == null || rt.getExpiration().isBefore(Instant.now())) {
                            return Uni.createFrom()
                                    .failure(new RuntimeException("Refresh token is invalid or expired"));
                        }

                        return userQueryPort
                                .findById(rt.getUserId().toHexString())
                                .chain(user -> {
                                    String newAccessToken = jwtUtil.generateToken(user.email(),
                                            Collections.singletonList("ROLE_USER"), user.id());
                                    String newRefreshTokenStr = jwtUtil.generateRefreshToken(user.email(),
                                            user.id());

                                    rt.setToken(newRefreshTokenStr);
                                    rt.setExpiration(
                                            Instant.now().plusMillis(jwtUtil.getRefreshExpirationMs()));

                                    return refreshTokenRepository.persist(rt)
                                            .map(v -> new String[] { newAccessToken, newRefreshTokenStr });
                                });
                    })
                    .invoke(tokens -> log.info("Successfully refreshed access token"));
        });
    }

    public Uni<Void> forgotPassword(String email) {
        Attributes attrs = Attributes.builder()
                .put("user.email", email)
                .build();

        return runTraced("forgotPassword", "forgot_password", attrs, () -> {
            log.info("Initiated forgot password process for email: {}", email);
            return userQueryPort
                    .findByEmail(email)
                    .chain(user -> {
                        if (user == null) {
                            return Uni.createFrom().failure(new RuntimeException("User not found"));
                        }

                        String token = UUID.randomUUID().toString();

                        ResetToken resetToken = new ResetToken();
                        resetToken.setUserId(new ObjectId(user.id()));
                        resetToken.setToken(token);
                        resetToken.setExpiration(Instant.now().plusMillis(900000)); // 15 mins

                        return resetTokenRepository.deleteByUserId(new ObjectId(user.id()))
                                .chain(() -> resetTokenRepository.persist(resetToken))
                                .chain(() -> sendForgotPasswordEmail(user, token));
                    })
                    .invoke(() -> log.info("Successfully sent forgot password token to: {}", email));
        });
    }

    public Uni<Void> resetPassword(ResetPasswordRequest req) {
        Attributes attrs = Attributes.builder()
                .put("operation", "reset_password")
                .build();

        return runTraced("resetPassword", "reset_password", attrs, () -> {
            log.info("Attempting reset password using reset token");
            if (!req.getPassword().equals(req.getConfirmPassword())) {
                return Uni.createFrom().failure(new RuntimeException("Passwords do not match"));
            }

            return resetTokenRepository.findByToken(req.getToken())
                    .chain(rt -> {
                        if (rt == null || rt.getExpiration().isBefore(Instant.now())) {
                            return Uni.createFrom().failure(new RuntimeException("Invalid or expired reset token"));
                        }

                        return userQueryPort
                                .findById(rt.getUserId().toHexString())
                                .chain(user -> userCommandPort.update(new UpdateUserCmd(
                                        user.id(),
                                        user.firstname(),
                                        user.lastname(),
                                        user.email(),
                                        req.getPassword(),
                                        req.getConfirmPassword())))
                                .chain(updated -> resetTokenRepository.delete(rt))
                                .replaceWithVoid();
                    })
                    .invoke(() -> log.info("Successfully reset password"));
        });
    }

    public Uni<Void> logout(String refreshTokenStr) {
        log.info("Attempting logout");
        return refreshTokenRepository.deleteByToken(refreshTokenStr)
                .replaceWithVoid()
                .invoke(() -> log.info("Successfully logged out"))
                .onFailure().invoke(err -> log.error("Logout failed, error: {}", err.getMessage()));
    }

    public Uni<Void> verifyEmailByCode(String code) {
        log.info("Attempting email verification with code: {}", code);
        String key = "verification_code:" + code;
        return redisService.getReactive(key)
                .chain(email -> {
                    if (email == null) {
                        return Uni.createFrom().failure(new RuntimeException("Invalid or expired verification code"));
                    }
                    return redisService.deleteReactive(key)
                            .chain(() -> redisService.deleteReactive("verification:" + email))
                            .replaceWithVoid();
                })
                .invoke(() -> log.info("Successfully verified email for code: {}", code))
                .onFailure()
                .invoke(err -> log.error("Email verification failed for code: {}, error: {}", code, err.getMessage()));
    }

    public Uni<UserResponse> getMe(String userId) {
        log.info("Fetching profile for user ID: {}", userId);
        return userQueryPort.findById(userId)
                .map(AuthService::toPbUser)
                .invoke(user -> log.info("Successfully retrieved profile for user: {}", user.getEmail()))
                .onFailure().invoke(err -> log.error("Failed to retrieve profile for user ID: {}, error: {}", userId,
                        err.getMessage()));
    }

    private Uni<String[]> handleFailedLogin(String email, String failedAttemptsKey, String lockKey) {
        log.warn("Failed login attempt for email: {}", email);
        return redisService.getReactive(failedAttemptsKey)
                .chain(attemptsStr -> {
                    int currentAttempts = attemptsStr == null ? 0 : Integer.parseInt(attemptsStr);
                    int newAttempts = currentAttempts + 1;
                    if (newAttempts >= 5) {
                        log.error("Account locked due to 5 consecutive failed login attempts: {}", email);
                        return redisService.setWithExpirationReactive(lockKey, "true", 3600) // lock 1 hr
                                .chain(() -> redisService.deleteReactive(failedAttemptsKey))
                                .chain(() -> Uni.createFrom().failure(
                                        new RuntimeException("Account is locked due to too many failed attempts")));
                    } else {
                        log.warn("Incrementing failed login attempts for email: {}. Current failed attempts: {}", email,
                                newAttempts);
                        return redisService
                                .setWithExpirationReactive(failedAttemptsKey, String.valueOf(newAttempts), 600) // 10
                                                                                                                // mins
                                .chain(() -> Uni.createFrom().failure(
                                        new RuntimeException("Invalid credentials. Attempt " + newAttempts + " of 5")));
                    }
                });
    }

    private static UserResponse toPbUser(User u) {
        UserResponse.Builder builder = UserResponse.newBuilder()
                .setId(u.id())
                .setFirstname(u.firstname())
                .setLastname(u.lastname())
                .setEmail(u.email());
        if (u.createdAt() != null) {
            builder.setCreatedAt(u.createdAt().toString());
        }
        if (u.updatedAt() != null) {
            builder.setUpdatedAt(u.updatedAt().toString());
        }
        return builder.build();
    }

    /**
     * Writes the welcome-email event into the outbox table (transactional
     * outbox pattern) instead of publishing to Kafka directly — the row is
     * flushed by {@code OutboxPublisher} in the same DB transaction as the
     * user creation. Kafka being down no longer loses the notification.
     */
    private Uni<Void> sendWelcomeEmail(User user, String code) {
        String subject = "Welcome to Quarkus Modular Monolith";
        String body = String.format(
                "Hello %s %s,\n\nWelcome to our platform! Use the following code to verify your email address:\n\n%s\n\nRegards,\nSupport Team",
                user.firstname(), user.lastname(), code);

        JsonObject payload = new JsonObject()
                .put("email", user.email())
                .put("subject", subject)
                .put("body", body);

        return persistOutboxEvent("USER", user.id(),
                "email-service-topic-auth-register", payload);
    }

    private Uni<Void> sendForgotPasswordEmail(User user, String token) {
        String subject = "Reset Password Verification";
        String body = String.format(
                "Hello %s %s,\n\nYou have requested a password reset. Use the following token to reset your password:\n\n%s\n\nThis token will expire in 15 minutes.\n\nRegards,\nSupport Team",
                user.firstname(), user.lastname(), token);

        JsonObject payload = new JsonObject()
                .put("email", user.email())
                .put("subject", subject)
                .put("body", body);

        return persistOutboxEvent("USER", user.id(),
                "email-service-topic-auth-forgot-password", payload);
    }

    private Uni<Void> persistOutboxEvent(String aggregateType, String aggregateId, String topic, JsonObject payload) {
        Outbox outbox = new Outbox();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setTopic(topic);
        // Phase 2 (event contract): attach the standard envelope (event_id,
        // schema_version, event_type, occurred_at) before persisting, so the
        // outbox replay keeps a stable event_id.
        outbox.setPayload(com.sanedge.common.event.EventEnvelope.withDefaults(payload, topic).encode());
        return outboxRepository.persist(outbox).replaceWithVoid();
    }

    private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
            Supplier<Uni<T>> supplier) {
        return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
    }
}
