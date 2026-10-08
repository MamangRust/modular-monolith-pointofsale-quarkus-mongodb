package com.sanedge.transaction.service.impl;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.adapter.merchant.MerchantQueryPort;
import com.sanedge.common.adapter.order.OrderQueryPort;
import com.sanedge.common.adapter.order_item.OrderItemQueryPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.enums.PaymentStatus;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.transaction.domain.requests.CreateTransactionRequest;
import com.sanedge.transaction.domain.requests.UpdateTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.entity.Outbox;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.repository.OutboxRepository;
import com.sanedge.transaction.repository.TransactionCommandRepository;
import com.sanedge.transaction.repository.TransactionQueryRepository;
import com.sanedge.transaction.service.TransactionCommandService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TransactionCommandServiceImpl implements TransactionCommandService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionCommandServiceImpl.class);

        private final TransactionQueryRepository transactionQueryRepository;
        private final TransactionCommandRepository transactionCommandRepository;
        private final OutboxRepository outboxRepository;
        private final RedisService redisService;
        private final TracingMetrics tracingMetrics;
        private final MerchantQueryPort merchantQueryPort;
        private final OrderQueryPort orderQueryPort;
        private final OrderItemQueryPort orderItemQueryPort;

        @ConfigProperty(name = "notification.transaction.email", defaultValue = "admin@example.com")
        String notificationEmail;

        @Inject
        public TransactionCommandServiceImpl(TransactionQueryRepository transactionQueryRepository,
                        TransactionCommandRepository transactionCommandRepository,
                        OutboxRepository outboxRepository,
                        RedisService redisService,
                        TracingMetrics tracingMetrics,
                        MerchantQueryPort merchantQueryPort,
                        OrderQueryPort orderQueryPort,
                        OrderItemQueryPort orderItemQueryPort) {
                this.transactionQueryRepository = transactionQueryRepository;
                this.transactionCommandRepository = transactionCommandRepository;
                this.outboxRepository = outboxRepository;
                this.redisService = redisService;
                this.tracingMetrics = tracingMetrics;
                this.merchantQueryPort = merchantQueryPort;
                this.orderQueryPort = orderQueryPort;
                this.orderItemQueryPort = orderItemQueryPort;
        }

        private Uni<Void> clearCache(String transactionId, String orderId) {
                String idKey = "transaction:id:" + transactionId;
                String orderKey = "transaction:order:" + orderId;

                return Uni.combine().all().unis(
                                redisService.deleteReactive(idKey),
                                redisService.deleteReactive(orderKey)).discardItems();
        }    @Override
    public Uni<ApiResponse<TransactionResponse>> create(CreateTransactionRequest req) {
        logger.info("Creating new transaction | orderId={}, merchantId={}", req.getOrderID(),
                        req.getMerchantID());

        if (req.getMerchantID() == null || req.getOrderID() == null
                        || req.getAmount() == null
                        || req.getPaymentMethod() == null) {
                logger.error("All fields are required");
                return Uni.createFrom().item(new ApiResponse<>("error",
                                "All fields are required",
                                (TransactionResponse) null));
        }

        // Idempotency (Fase 12): a client-sent key makes create replay-safe.
        // An existing active row with the same key short-circuits the whole
        // validation + insert chain and returns the stored transaction.
        String idempotencyKey = req.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                return transactionQueryRepository.findByIdempotencyKey(idempotencyKey)
                                .chain(existing -> {
                                        if (existing != null) {
                                                logger.info("Idempotent replay | key={} transactionId={}",
                                                                idempotencyKey, existing.getTransactionId());
                                                return Uni.createFrom().item(ApiResponse.success(
                                                                "Transaction already exists (idempotent replay)",
                                                                TransactionResponse.from(existing)));
                                        }
                                        return doCreate(req);
                                });
        }
        return doCreate(req);
    }

    private Uni<ApiResponse<TransactionResponse>> doCreate(CreateTransactionRequest req) {
        Attributes attrs = Attributes.builder()
                        .put("merchantId", req.getMerchantID())
                        .put("orderId", req.getOrderID())
                        .build();

        return runTraced("createTransaction", "create_transaction", attrs,
                        () -> merchantQueryPort.findByMerchantId(req.getMerchantID())
                                        .onItem().transformToUni(merchant -> orderQueryPort
                                                        .findById(req.getOrderID())
                                                        .onItem().transformToUni(order -> orderItemQueryPort
                                                                        .findOrderItemByOrder(req.getOrderID())
                                                                        .onItem().transformToUni(items -> {
                                                                                if (items.isEmpty()) {
                                                                                        logger.error("No order items found | orderId={}",
                                                                                                        req.getOrderID());
                                                                                        return Uni.createFrom()
                                                                                                        .item(errorResponse(
                                                                                                                        "No order items found"));
                                                                                }
                                                                                return chargeAmount(req.getAmount(), items,
                                                                                                (finalAmount, paymentStatus) -> {
                                                                                                        req.setAmount(finalAmount);
                                                                                                        req.setPaymentStatus(
                                                                                                                        paymentStatus);
                                                                                                        return persistNewTransaction(
                                                                                                                        req);
                                                                                                });
                                                                        }))
                                                        .onFailure(ResourceNotFoundException.class)
                                                        .recoverWithItem(e -> {
                                                                logger.error("Order not found | orderId={}",
                                                                                req.getOrderID());
                                                                return errorResponse("Order not found");
                                                        }))
                                        .onFailure(ResourceNotFoundException.class).recoverWithItem(e -> {
                                                logger.error("Merchant not found | merchantId={}",
                                                                req.getMerchantID());
                                                return errorResponse("Merchant not found");
                                        }));
    }

    private ApiResponse<TransactionResponse> errorResponse(String message) {
        return new ApiResponse<>("error", message, (TransactionResponse) null);
    }

    /**
     * Validates the order items and recomputes the amount (subtotal + 11% PPN)
     * before delegating to the given persistence step with the final amount and
     * payment status.
     */
    private Uni<ApiResponse<TransactionResponse>> chargeAmount(int requestedAmount,
                    java.util.List<com.sanedge.common.adapter.model.OrderItem> items,
                    java.util.function.BiFunction<Integer, String, Uni<ApiResponse<TransactionResponse>>> proceed) {
        for (var item : items) {
            if (item.quantity() <= 0) {
                return Uni.createFrom().item(errorResponse("Invalid order item quantity"));
            }
        }
        int totalAmount = items.stream().mapToInt(item -> item.price() * item.quantity()).sum();
        int ppn = totalAmount * 11 / 100;
        int totalAmountWithTax = totalAmount + ppn;

        String paymentStatus = requestedAmount >= totalAmountWithTax ? "success" : "failed";
        if ("failed".equals(paymentStatus)) {
            logger.error("Insufficient payment amount | amount={}, required={}", requestedAmount,
                            totalAmountWithTax);
            return Uni.createFrom().item(errorResponse("Insufficient payment amount"));
        }

        return proceed.apply(totalAmountWithTax, paymentStatus);
    }

    private Uni<ApiResponse<TransactionResponse>> persistNewTransaction(CreateTransactionRequest req) {
        Transaction transaction = new Transaction();
        transaction.setOrderId(req.getOrderID());
        transaction.setMerchantId(new ObjectId(req.getMerchantID()));
        transaction.setPaymentMethod(req.getPaymentMethod());
        transaction.setAmount(req.getAmount());
        transaction.setStatus(PaymentStatus.fromValue(req.getPaymentStatus()));
        transaction.setIdempotencyKey(req.getIdempotencyKey());
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());

        return transactionCommandRepository
                        .persist(transaction)
                        .chain(savedTx -> persistOutboxEvent(savedTx)
                                        .chain(() -> clearCache(savedTx.getTransactionId(),
                                                        savedTx.getOrderId())
                                                        .map(v -> {
                                                                logger.info("Transaction created successfully | transactionId={}",
                                                                                savedTx.getTransactionId());
                                                                return ApiResponse.success(
                                                                                "Transaction created successfully",
                                                                                TransactionResponse.from(savedTx));
                                                        })))
                        .onFailure(err -> isIdempotencyConflict(err)
                                        && req.getIdempotencyKey() != null
                                        && !req.getIdempotencyKey().isBlank())
                        .recoverWithUni(err -> transactionQueryRepository
                                        .findByIdempotencyKey(req.getIdempotencyKey())
                                        .map(existing -> {
                                                if (existing == null) {
                                                        throw new IllegalStateException(
                                                                        "Idempotency conflict but no existing transaction found for key="
                                                                                        + req.getIdempotencyKey());
                                                }
                                                logger.info("Idempotent replay after unique-violation | key={} transactionId={}",
                                                                req.getIdempotencyKey(),
                                                                existing.getTransactionId());
                                                return ApiResponse.success(
                                                                "Transaction already exists (idempotent replay)",
                                                                TransactionResponse.from(existing));
                                        }));
    }

    @Override
    public Uni<ApiResponse<TransactionResponse>> update(UpdateTransactionRequest req) {
        logger.info("Updating transaction | transactionId={}", req.getTransactionID());
        Attributes attrs = Attributes.builder()
                        .put("transactionId", req.getTransactionID())
                        .build();

        return runTraced("updateTransaction", "update_transaction", attrs,
                        () -> {
                                if (req.getTransactionID() == null || req.getMerchantID() == null
                                                || req.getOrderID() == null
                                                || req.getAmount() == null || req.getPaymentMethod() == null) {
                                        return Uni.createFrom().item(errorResponse("All fields are required"));
                                }

                                return transactionQueryRepository
                                                .findByTransactionId(req.getTransactionID())
                                                .chain(existingTx -> {
                                                        if (existingTx == null) {
                                                                logger.error("Transaction not found | transactionId={}",
                                                                                req.getTransactionID());
                                                                return Uni.createFrom()
                                                                                .item(errorResponse(
                                                                                                "Transaction not found"));
                                                        }

                                                        if (PaymentStatus.SUCCESS.equals(existingTx.getStatus())
                                                                        || PaymentStatus.REFUNDED.equals(
                                                                                        existingTx.getStatus())) {
                                                                logger.error("Transaction cannot be modified | transactionId={}",
                                                                                req.getTransactionID());
                                                                return Uni.createFrom().item(errorResponse(
                                                                                "Transaction cannot be modified"));
                                                        }

                                                        return merchantQueryPort.findByMerchantId(req.getMerchantID())
                                                                        .onItem().transformToUni(merchant -> orderQueryPort
                                                                                        .findById(req.getOrderID())
                                                                                        .onItem().transformToUni(order -> orderItemQueryPort
                                                                                                        .findOrderItemByOrder(
                                                                                                                        req.getOrderID())
                                                                                                        .onItem().transformToUni(items -> {
                                                                                                                if (items.isEmpty()) {
                                                                                                                        logger.error("No order items found | orderId={}",
                                                                                                                                        req.getOrderID());
                                                                                                                        return Uni.createFrom()
                                                                                                                                        .item(errorResponse(
                                                                                                                                                        "No order items found"));
                                                                                                                }
                                                                                                                return chargeAmount(
                                                                                                                                req.getAmount(), items,
                                                                                                                                (finalAmount, paymentStatus) -> {
                                                                                                                                        req.setAmount(
                                                                                                                                                        finalAmount);
                                                                                                                                        req.setPaymentStatus(
                                                                                                                                                        paymentStatus);
                                                                                                                                        return persistUpdatedTransaction(
                                                                                                                                                        req, existingTx);
                                                                                                                                });
                                                                                                        }))
                                                                                        .onFailure(ResourceNotFoundException.class)
                                                                                        .recoverWithItem(e -> {
                                                                                                logger.error("Order not found | orderId={}",
                                                                                                                req.getOrderID());
                                                                                                return errorResponse(
                                                                                                                "Order not found");
                                                                                        }))
                                                                        .onFailure(ResourceNotFoundException.class)
                                                                        .recoverWithItem(e -> {
                                                                                logger.error("Merchant not found | merchantId={}",
                                                                                                req.getMerchantID());
                                                                                return errorResponse(
                                                                                                "Merchant not found");
                                                                        });
                                                });
                        });
    }

    private Uni<ApiResponse<TransactionResponse>> persistUpdatedTransaction(UpdateTransactionRequest req,
                    Transaction existingTx) {
        existingTx.setOrderId(req.getOrderID());
        existingTx.setMerchantId(new ObjectId(req.getMerchantID()));
        existingTx.setPaymentMethod(req.getPaymentMethod());
        existingTx.setAmount(req.getAmount());
        existingTx.setStatus(PaymentStatus.fromValue(req.getPaymentStatus()));
        existingTx.setUpdatedAt(Instant.now());

        return transactionCommandRepository
                        .persist(existingTx)
                        .chain(savedTx -> clearCache(savedTx.getTransactionId(), savedTx.getOrderId())
                                        .map(v -> {
                                                logger.info("Transaction updated successfully | transactionId={}",
                                                                savedTx.getTransactionId());
                                                return ApiResponse.success("Transaction updated successfully",
                                                                TransactionResponse.from(savedTx));
                                        }));
    }

        @Override
        public Uni<ApiResponse<TransactionResponseDeleteAt>> trash(String id) {
                logger.info("Trashing transaction id={}", id);
                Attributes attrs = Attributes.builder()
                                .put("transactionId", id)
                                .build();

                return runTraced("trashTransaction", "trash_transaction", attrs,
                                () -> transactionCommandRepository.trashed(id)
                                                .chain(transaction -> {
                                                        if (transaction == null) {
                                                                return Uni.createFrom().item(new ApiResponse<>("error",
                                                                                "Transaction not found",
                                                                                (TransactionResponseDeleteAt) null));
                                                        }
                                                        return clearCache(transaction.getTransactionId(),
                                                                        transaction.getOrderId())
                                                                        .map(v -> {
                                                                                logger.info("Transaction trashed successfully for id={}",
                                                                                                id);
                                                                                return ApiResponse.success(
                                                                                                "Transaction trashed successfully",
                                                                                                TransactionResponseDeleteAt
                                                                                                                .from(transaction));
                                                                        });
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to trash transaction id={}: {}", id,
                                                                        e.getMessage(), e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to trash transaction: "
                                                                                        + e.getMessage(),
                                                                        (TransactionResponseDeleteAt) null);
                                                }));
        }

        @Override
        public Uni<ApiResponse<TransactionResponseDeleteAt>> restore(String id) {
                logger.info("Restoring transaction id={}", id);
                Attributes attrs = Attributes.builder()
                                .put("transactionId", id)
                                .build();

                return runTraced("restoreTransaction", "restore_transaction", attrs,
                                () -> transactionCommandRepository.restore(id)
                                                .chain(transaction -> {
                                                        if (transaction == null) {
                                                                return Uni.createFrom().item(new ApiResponse<>("error",
                                                                                "Transaction not found",
                                                                                (TransactionResponseDeleteAt) null));
                                                        }
                                                        return clearCache(transaction.getTransactionId(),
                                                                        transaction.getOrderId())
                                                                        .map(v -> {
                                                                                logger.info("Transaction restored successfully for id={}",
                                                                                                id);
                                                                                return ApiResponse.success(
                                                                                                "Transaction restored successfully",
                                                                                                TransactionResponseDeleteAt
                                                                                                                .from(transaction));
                                                                        });
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to restore transaction id={}: {}", id,
                                                                        e.getMessage(), e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to restore transaction: "
                                                                                        + e.getMessage(),
                                                                        (TransactionResponseDeleteAt) null);
                                                }));
        }

        @Override
        public Uni<ApiResponse<Boolean>> delete(String id) {
                logger.info("Permanently deleting transaction id={}", id);
                Attributes attrs = Attributes.builder()
                                .put("transactionId", id)
                                .build();

                return runTraced("deleteTransaction", "delete_transaction", attrs,
                                () -> transactionCommandRepository.deletePermanent(id)
                                                .chain(transaction -> {
                                                        if (transaction == null) {
                                                                return Uni.createFrom()
                                                                                .item(new ApiResponse<>("error",
                                                                                                "Transaction not found or not trashed",
                                                                                                false));
                                                        }
                                                        return clearCache(transaction.getTransactionId(),
                                                                        transaction.getOrderId())
                                                                        .map(v -> {
                                                                                logger.info("Transaction permanently deleted for id={}",
                                                                                                id);
                                                                                return ApiResponse.success(
                                                                                                "Transaction permanently deleted",
                                                                                                true);
                                                                        });
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to delete transaction id={}: {}", id,
                                                                        e.getMessage(), e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to delete transaction: "
                                                                                        + e.getMessage(),
                                                                        false);
                                                }));
        }

        @Override
        public Uni<ApiResponse<Boolean>> restoreAll() {
                logger.info("Restoring ALL trashed transactions");

                return runTraced("restoreAllTransactions", "restore_all_transactions", Attributes.empty(),
                                () -> transactionCommandRepository.restoreAllDeleted()
                                                .map(success -> {
                                                        if (!success) {
                                                                throw new ResourceNotFoundException(
                                                                                "No transactions found in trash");
                                                        }

                                                        logger.info("All transactions restored successfully");
                                                        return ApiResponse.success(
                                                                        "All transactions restored successfully",
                                                                        success);
                                                }));
        }

        @Override
        public Uni<ApiResponse<Boolean>> deleteAll() {
                logger.info("Permanently deleting ALL trashed transactions");

                return runTraced("deleteAllTransactions", "delete_all_transactions", Attributes.empty(),
                                () -> transactionCommandRepository.deleteAllDeleted()
                                                .map(success -> {
                                                        if (!success) {
                                                                throw new ResourceNotFoundException(
                                                                                "No transactions found in trash");
                                                        }

                                                        logger.info("All trashed transactions permanently deleted");
                                                        return ApiResponse.success(
                                                                        "All trashed transactions permanently deleted",
                                                                        success);
                                                }));
        }

        /**
         * Writes the notification email event into the outbox collection so it is
         * published to Kafka by {@code OutboxPublisher} — reliably, outside the
         * request path (transactional outbox pattern). Persisted right after the
         * {@code transactions} insert.
         */
        private Uni<Void> persistOutboxEvent(Transaction tx) {
                if (notificationEmail == null || notificationEmail.isBlank()) {
                        logger.warn("notification.transaction.email not configured; skipping outbox event for transactionId={}",
                                        tx.getTransactionId());
                        return Uni.createFrom().voidItem();
                }

                JsonObject payload = new JsonObject()
                                .put("email", notificationEmail)
                                .put("subject", "New Transaction Created")
                                .put("body", String.format(
                                                "A new transaction of <b>%d</b> using <b>%s</b> has been created. Status: <b>%s</b>.",
                                                tx.getAmount(), tx.getPaymentMethod(), tx.getStatus()));

                Outbox outbox = new Outbox();
                outbox.setAggregateType("TRANSACTION");
                outbox.setAggregateId(String.valueOf(tx.getTransactionId()));
                outbox.setTopic("email-service-topic-transaction-create");
                // Phase 2 (event contract): attach the standard envelope
                // (event_id, schema_version, event_type, occurred_at) before
                // persisting, so the outbox replay keeps a stable event_id.
                outbox.setPayload(com.sanedge.common.event.EventEnvelope
                                .withDefaults(payload, "email-service-topic-transaction-create").encode());

                return outboxRepository.persist(outbox).replaceWithVoid();
        }

        private boolean isIdempotencyConflict(Throwable err) {
                for (Throwable t = err; t != null; t = t.getCause()) {
                        String msg = t.getMessage();
                        if (msg != null && (msg.contains("duplicate key")
                                        || msg.contains("E11000")
                                        || msg.contains("11000"))) {
                                return true;
                        }
                }
                return false;
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        java.util.function.Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}