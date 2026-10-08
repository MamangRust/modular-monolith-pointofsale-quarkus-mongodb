package com.sanedge.auth.repository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;

import com.sanedge.auth.entity.RefreshToken;
import com.sanedge.auth.entity.ResetToken;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class AuthRepositoryTest extends MongoTestBase {

    @Inject
    RefreshTokenRepository refreshTokenRepo;

    @Inject
    ResetTokenRepository resetTokenRepo;

    private Uni<RefreshToken> createRefreshToken(String token, org.bson.types.ObjectId userId) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(token);
        rt.setUserId(userId);
        rt.setExpiration(Instant.now().plusMillis(3600000));
        return RefreshToken.persist(rt).replaceWith(rt);
    }

    private Uni<ResetToken> createResetToken(String token, org.bson.types.ObjectId userId) {
        ResetToken rt = new ResetToken();
        rt.setToken(token);
        rt.setUserId(userId);
        rt.setExpiration(Instant.now().plusMillis(900000));
        return ResetToken.persist(rt).replaceWith(rt);
    }

    private Uni<Void> cleanUpRefreshTokens() {
        return RefreshToken.deleteAll().replaceWithVoid();
    }

    private Uni<Void> cleanUpResetTokens() {
        return ResetToken.deleteAll().replaceWithVoid();
    }

    @Test
    void persistRefreshToken() {
        cleanUpRefreshTokens()
                .chain(() -> {
                    RefreshToken token = new RefreshToken();
                    token.setToken("test-refresh-token");
                    token.setUserId(new org.bson.types.ObjectId("507f1f77bcf86cd799439011"));
                    token.setExpiration(Instant.now().plusMillis(3600000));
                    return RefreshToken.persist(token).replaceWith(token);
                })
                .invoke(saved -> assertThat(saved.id).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void findRefreshTokenByToken() {
        cleanUpRefreshTokens()
                .chain(() -> createRefreshToken("find-me-token", new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .chain(ignored -> refreshTokenRepo.findByToken("find-me-token"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getToken()).isEqualTo("find-me-token");
                    assertThat(found.getUserId()).isEqualTo(new org.bson.types.ObjectId("507f1f77bcf86cd799439011"));
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void findRefreshTokenByUserId() {
        cleanUpRefreshTokens()
                .chain(() -> createRefreshToken("user-token", new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .chain(ignored -> refreshTokenRepo.findByUserId(new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getUserId()).isEqualTo(new org.bson.types.ObjectId("507f1f77bcf86cd799439011"));
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void deleteRefreshTokenByUserId() {
        cleanUpRefreshTokens()
                .chain(() -> createRefreshToken("delete-by-user", new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .chain(ignored -> refreshTokenRepo.deleteByUserId(new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .invoke(deleted -> assertThat(deleted).isPositive())
                .chain(ignored -> refreshTokenRepo.findByUserId(new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void deleteRefreshTokenByToken() {
        cleanUpRefreshTokens()
                .chain(() -> createRefreshToken("delete-by-token", new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .chain(ignored -> refreshTokenRepo.deleteByToken("delete-by-token"))
                .invoke(deleted -> assertThat(deleted).isPositive())
                .chain(ignored -> refreshTokenRepo.findByToken("delete-by-token"))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void persistResetToken() {
        cleanUpResetTokens()
                .chain(() -> {
                    ResetToken rt = new ResetToken();
                    rt.setToken("reset-token-001");
                    rt.setUserId(new org.bson.types.ObjectId("507f1f77bcf86cd799439011"));
                    rt.setExpiration(Instant.now().plusMillis(900000));
                    return ResetToken.persist(rt).replaceWith(rt);
                })
                .invoke(saved -> assertThat(saved.id).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void findResetTokenByToken() {
        cleanUpResetTokens()
                .chain(() -> createResetToken("find-reset-token", new org.bson.types.ObjectId("507f1f77bcf86cd799439011")))
                .chain(ignored -> resetTokenRepo.findByToken("find-reset-token"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getToken()).isEqualTo("find-reset-token");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void deleteResetTokenByUserId() {
        cleanUpResetTokens()
                .chain(() -> createResetToken("reset-delete-user", new org.bson.types.ObjectId("507f1f77bcf86cd799439012")))
                .chain(ignored -> resetTokenRepo.deleteByUserId(new org.bson.types.ObjectId("507f1f77bcf86cd799439012")))
                .invoke(deleted -> assertThat(deleted).isPositive())
                .chain(ignored -> resetTokenRepo.findByToken("reset-delete-user"))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }
}
