package com.sanedge.order_item.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sanedge.order_item.domain.requests.FindAllOrderItems;
import com.sanedge.order_item.entity.OrderItem;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderItemRepositoryTest extends MongoTestBase {

    private static final String ORDER_ID = "507f1f77bcf86cd7994390a1";

    @Inject
    OrderItemRepository repository;

    private Uni<OrderItem> persistItem(String orderId, String productId, int quantity, int price) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId != null ? new org.bson.types.ObjectId(orderId) : null);
        item.setProductId(productId != null ? new org.bson.types.ObjectId(productId) : null);
        item.setQuantity(quantity);
        item.setPrice(price);
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return repository.persist(item).map(i -> i);
    }

    private Uni<OrderItem> persistItem(String orderId, String productId) {
        return persistItem(orderId, productId, 1, 5000);
    }

    private Uni<Void> clean() {
        return repository.deleteAll().replaceWithVoid();
    }

    private FindAllOrderItems findAllReq(int page, int size, String search) {
        FindAllOrderItems req = new FindAllOrderItems();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    @Test
    void testCreateAndFindByOrder() {
        clean()
                .chain(() -> persistItem(ORDER_ID, "507f1f77bcf86cd7994390b1"))
                .chain(() -> persistItem(ORDER_ID, "507f1f77bcf86cd7994390b2"))
                .chain(() -> repository.findOrderItemByOrder(ORDER_ID))
                .invoke(items -> {
                    assertThat(items).hasSize(2);
                    assertThat(items).allSatisfy(item -> assertThat(item.getOrderId().toHexString()).isEqualTo(ORDER_ID));
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindOrderItemByOrderIncludingDeleted() {
        clean()
                .chain(() -> persistItem(ORDER_ID, "507f1f77bcf86cd7994390b1"))
                .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid())
                .chain(() -> persistItem(ORDER_ID, "507f1f77bcf86cd7994390b2"))
                .chain(() -> repository.findOrderItemByOrder(ORDER_ID))
                .invoke(activeItems -> assertThat(activeItems).hasSize(1)) // trashed excluded
                .chain(() -> repository.findOrderItemByOrderIncludingDeleted(ORDER_ID))
                .invoke(allItems -> assertThat(allItems).hasSize(2)) // includes trashed
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindOrderItemsWithSearch() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c2", "507f1f77bcf86cd7994390d2"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c3", "507f1f77bcf86cd7994390d3"))
                .chain(() -> repository.findOrderItems(findAllReq(1, 10, "c2")))
                .invoke(result -> {
                    // search matches id or orderId or productId containing "c2"
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getOrderId().toHexString()).isEqualTo("507f1f77bcf86cd7994390c2");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindOrderItemsPagination() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c2", "507f1f77bcf86cd7994390d2"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c3", "507f1f77bcf86cd7994390d3"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c4", "507f1f77bcf86cd7994390d4"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c5", "507f1f77bcf86cd7994390d5"))
                .chain(() -> repository.findOrderItems(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> repository.findOrderItems(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveOrderItemsExcludesTrashed() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d2")
                        .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> repository.findActiveOrderItems(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedOrderItemsOnlyTrashed() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d2")
                        .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> repository.findTrashedOrderItems(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getProductId().toHexString()).isEqualTo("507f1f77bcf86cd7994390d2");
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashOrderItem() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(item -> repository.trashed(item.getOrderItemId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(item -> repository.trashed(item.getOrderItemId())
                        .chain(() -> repository.trashed(item.getOrderItemId())))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreOrderItem() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(item -> repository.trashed(item.getOrderItemId())
                        .chain(() -> repository.restore(item.getOrderItemId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreNonExistentReturnsNull() {
        clean()
                .chain(() -> repository.restore("507f1f77bcf86cd7994390ff"))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(item -> repository.trashed(item.getOrderItemId())
                        .chain(() -> repository.deletePermanent(item.getOrderItemId())))
                .invoke(deleted -> assertThat(deleted).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentActiveReturnsNull() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(item -> repository.deletePermanent(item.getOrderItemId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1")
                        .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c2", "507f1f77bcf86cd7994390d2")
                        .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> repository.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> repository.findTrashedOrderItems(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1")
                        .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c2", "507f1f77bcf86cd7994390d2")
                        .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> persistItem("507f1f77bcf86cd7994390c3", "507f1f77bcf86cd7994390d3")) // stays active
                .chain(() -> repository.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> repository.findActiveOrderItems(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords() {
        clean()
                .chain(() -> repository.findOrderItems(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> repository.findActiveOrderItems(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> repository.findTrashedOrderItems(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testSearchNoMatchReturnsZero() {
        clean()
                .chain(() -> persistItem("507f1f77bcf86cd7994390c1", "507f1f77bcf86cd7994390d1"))
                .chain(() -> repository.findOrderItems(findAllReq(1, 10, "zzzzzzzzzzzzzzzzzzzzzzzz")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }
}
