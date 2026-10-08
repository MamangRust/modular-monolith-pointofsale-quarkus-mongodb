package com.sanedge.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.enums.PaymentStatus;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.transaction.domain.requests.FindAllTransactionByMerchantRequest;
import com.sanedge.transaction.domain.requests.FindAllTransactionRequest;
import com.sanedge.transaction.entity.Transaction;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class TransactionRepositoryTest extends MongoTestBase {

    private static final String MERCHANT_A = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_B = "507f1f77bcf86cd799439012";
    private static final String MISSING_ID = "507f1f77bcf86cd799439099";

    @Inject
    TransactionQueryRepository queryRepo;

    @Inject
    TransactionCommandRepository commandRepo;

    // ---------- helpers ----------
    private Uni<Transaction> persistTransaction(String merchantId, String paymentMethod, PaymentStatus status) {
        Transaction tx = new Transaction();
        tx.setMerchantId(new ObjectId(merchantId));
        tx.setPaymentMethod(paymentMethod);
        tx.setStatus(status);
        tx.setAmount(100000);
        tx.setOrderId(null); // optional
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        return queryRepo.persist(tx).replaceWith(tx);
    }

    private Uni<Transaction> persistTransaction(String merchantId, String paymentMethod) {
        return persistTransaction(merchantId, paymentMethod, PaymentStatus.PENDING);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllTransactionRequest findAllReq(int page, int size, String search) {
        FindAllTransactionRequest req = new FindAllTransactionRequest();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    private FindAllTransactionByMerchantRequest findAllByMerchantReq(String merchantId, int page, int size, String search) {
        FindAllTransactionByMerchantRequest req = new FindAllTransactionByMerchantRequest();
        req.setMerchantId(merchantId);
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindByTransactionId() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT", PaymentStatus.SUCCESS))
                .chain(tx -> queryRepo.findByTransactionId(tx.getTransactionId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getPaymentMethod()).isEqualTo("CREDIT");
                    assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByTransactionIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> queryRepo.findByTransactionId(MISSING_ID))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByOrderId() {
        clean()
                .chain(() -> {
                    Transaction tx = new Transaction();
                    tx.setMerchantId(new ObjectId(MERCHANT_A));
                    tx.setPaymentMethod("DEBIT");
                    tx.setStatus(PaymentStatus.PENDING);
                    tx.setAmount(50000);
                    tx.setOrderId("555");
                    tx.setCreatedAt(Instant.now());
                    tx.setUpdatedAt(Instant.now());
                    return queryRepo.persist(tx);
                })
                .chain(tx -> queryRepo.findByOrderId("555"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getOrderId()).isEqualTo("555");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByOrderIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> queryRepo.findByOrderId("99999"))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdempotencyKey() {
        clean()
                .chain(() -> {
                    Transaction tx = new Transaction();
                    tx.setMerchantId(new ObjectId(MERCHANT_A));
                    tx.setPaymentMethod("CASH");
                    tx.setStatus(PaymentStatus.PENDING);
                    tx.setAmount(50000);
                    tx.setIdempotencyKey("idem-repo-1");
                    tx.setCreatedAt(Instant.now());
                    tx.setUpdatedAt(Instant.now());
                    return queryRepo.persist(tx);
                })
                .chain(tx -> queryRepo.findByIdempotencyKey("idem-repo-1"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getIdempotencyKey()).isEqualTo("idem-repo-1");
                })
                .chain(() -> queryRepo.findByIdempotencyKey("idem-repo-missing"))
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindTransactionsWithSearchByPaymentMethod() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(() -> persistTransaction(MERCHANT_A, "DEBIT"))
                .chain(() -> persistTransaction(MERCHANT_A, "CASH"))
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 10, "credit")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getPaymentMethod()).isEqualTo("CREDIT");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTransactionsPagination() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "A"))
                .chain(() -> persistTransaction(MERCHANT_A, "B"))
                .chain(() -> persistTransaction(MERCHANT_A, "C"))
                .chain(() -> persistTransaction(MERCHANT_A, "D"))
                .chain(() -> persistTransaction(MERCHANT_A, "E"))
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findTransactions(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveTransactionsExcludesTrashed() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(() -> persistTransaction(MERCHANT_A, "DEBIT").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveTransactions(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedTransactionsOnlyTrashed() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(() -> persistTransaction(MERCHANT_A, "DEBIT").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedTransactions(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getPaymentMethod()).isEqualTo("DEBIT");
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== findByMerchant ====================

    @Test
    void testFindTransactionsByMerchant() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(() -> persistTransaction(MERCHANT_A, "CASH"))
                .chain(() -> persistTransaction(MERCHANT_B, "DEBIT"))
                .chain(() -> queryRepo.findTransactionsByMerchant(findAllByMerchantReq(MERCHANT_A, 1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                    assertThat(result.getData().stream()
                            .allMatch(tx -> tx.getMerchantId().toHexString().equals(MERCHANT_A))).isTrue();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTransactionsByMerchantWithSearch() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "QRIS", PaymentStatus.SUCCESS))
                .chain(() -> persistTransaction(MERCHANT_A, "BANK_TRANSFER", PaymentStatus.FAILED))
                .chain(() -> persistTransaction(MERCHANT_A, "CASH", PaymentStatus.PENDING))
                .chain(() -> queryRepo.findTransactionsByMerchant(findAllByMerchantReq(MERCHANT_A, 1, 10, "failed")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getPaymentMethod()).isEqualTo("BANK_TRANSFER");
                    assertThat(result.getData().get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashTransaction() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId())
                        .chain(() -> commandRepo.trashed(tx.getTransactionId())))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreTransaction() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId())
                        .chain(() -> commandRepo.restore(tx.getTransactionId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreNonExistentReturnsNull() {
        clean()
                .chain(() -> commandRepo.restore(MISSING_ID))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId())
                        .chain(() -> commandRepo.deletePermanent(tx.getTransactionId()))
                        .chain(perm -> queryRepo.findByTransactionId(tx.getTransactionId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentActiveReturnsNull() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(tx -> commandRepo.deletePermanent(tx.getTransactionId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "A").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> persistTransaction(MERCHANT_A, "B").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedTransactions(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "X").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> persistTransaction(MERCHANT_A, "Y").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> persistTransaction(MERCHANT_A, "Z")) // stays active
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveTransactions(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords() {
        clean()
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testSearchNoMatchReturnsZero() {
        clean()
                .chain(() -> persistTransaction(MERCHANT_A, "CREDIT"))
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }
}
