package com.sanedge.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.order.entity.Order;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderRepositoryTest extends MongoTestBase {

    private static final String MERCHANT_A = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_B = "507f1f77bcf86cd799439012";
    private static final String CASHIER_1 = "507f1f77bcf86cd799439021";
    private static final String CASHIER_2 = "507f1f77bcf86cd799439022";
    private static final String CASHIER_3 = "507f1f77bcf86cd799439023";
    private static final String MISSING_ID = "507f1f77bcf86cd799439099";

    @Inject
    OrderQueryRepository queryRepo;

    @Inject
    OrderCommandRepository commandRepo;

    // ---------- helpers ----------
    private Uni<Order> persistOrder(String cashierId, String merchantId, Long totalPrice) {
        Order order = new Order();
        order.setCashierId(new ObjectId(cashierId));
        order.setMerchantId(new ObjectId(merchantId));
        order.setTotalPrice(totalPrice);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return queryRepo.persist(order).replaceWith(order);
    }

    private Uni<Order> persistOrder(String cashierId, String merchantId) {
        return persistOrder(cashierId, merchantId, 100000L);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllOrderRequest findAllReq(int page, int size, String search) {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    private FindAllOrderByMerchantRequest findAllByMerchantReq(String merchantId, int page, int size, String search) {
        FindAllOrderByMerchantRequest req = new FindAllOrderByMerchantRequest();
        req.setMerchantId(merchantId);
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindById() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A, 150000L))
                .chain(o -> queryRepo.findOrderById(o.getOrderId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getCashierId()).isEqualTo(new ObjectId(CASHIER_1));
                    assertThat(found.getMerchantId()).isEqualTo(new ObjectId(MERCHANT_A));
                    assertThat(found.getTotalPrice()).isEqualTo(150000L);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> queryRepo.findOrderById(MISSING_ID))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindOrdersWithSearchByCashierId() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_3, MERCHANT_A))
                .chain(() -> queryRepo.findOrders(findAllReq(1, 10, "439021")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getCashierId()).isEqualTo(new ObjectId(CASHIER_1));
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindOrdersPagination() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_3, MERCHANT_A))
                .chain(() -> queryRepo.findOrders(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(3);
                })
                .chain(() -> queryRepo.findOrders(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindOrdersByMerchant() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_3, MERCHANT_B))
                .chain(() -> queryRepo.findOrdersByMerchant(findAllByMerchantReq(MERCHANT_A, 1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                    assertThat(result.getData().stream()
                            .allMatch(o -> o.getMerchantId().toHexString().equals(MERCHANT_A))).isTrue();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindOrdersByMerchantWithSearch() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_3, MERCHANT_A))
                .chain(() -> queryRepo.findOrdersByMerchant(findAllByMerchantReq(MERCHANT_A, 1, 10, "439022")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getCashierId()).isEqualTo(new ObjectId(CASHIER_2));
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveOrdersExcludesTrashed() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A)
                        .chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveOrders(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedOrdersOnlyTrashed() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A)
                        .chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getCashierId()).isEqualTo(new ObjectId(CASHIER_2));
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashOrder() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(o -> commandRepo.trashed(o.getOrderId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(o -> commandRepo.trashed(o.getOrderId())
                        .chain(() -> commandRepo.trashed(o.getOrderId())))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreOrder() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(o -> commandRepo.trashed(o.getOrderId())
                        .chain(() -> commandRepo.restore(o.getOrderId())))
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
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(o -> commandRepo.trashed(o.getOrderId())
                        .chain(() -> commandRepo.deletePermanent(o.getOrderId()))
                        .chain(() -> queryRepo.findOrderById(o.getOrderId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentActiveReturnsNull() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(o -> commandRepo.deletePermanent(o.getOrderId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A)
                        .chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A)
                        .chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A)
                        .chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> persistOrder(CASHIER_2, MERCHANT_A)
                        .chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> persistOrder(CASHIER_3, MERCHANT_A)) // stays active
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveOrders(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords() {
        clean()
                .chain(() -> queryRepo.findOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testSearchNoMatchReturnsZero() {
        clean()
                .chain(() -> persistOrder(CASHIER_1, MERCHANT_A))
                .chain(() -> queryRepo.findOrders(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }
}
