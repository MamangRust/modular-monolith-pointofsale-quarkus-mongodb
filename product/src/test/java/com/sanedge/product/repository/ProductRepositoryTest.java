package com.sanedge.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.product.domain.requests.FindAllProductByMerchantRequest;
import com.sanedge.product.domain.requests.FindAllProductByCategoryRequest;
import com.sanedge.product.domain.requests.FindAllProductRequest;
import com.sanedge.product.entity.Product;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class ProductRepositoryTest extends MongoTestBase {

    @Inject
    ProductQueryRepository queryRepo;

    @Inject
    ProductCommandRepository commandRepo;

    // ---------- helpers ----------
    private Uni<Product> persistProduct(String name, String merchantId, String categoryId, int price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setMerchantId(new ObjectId(merchantId));
        p.setCategoryId(new ObjectId(categoryId));
        p.setPrice(price);
        p.setCountInStock(stock);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return queryRepo.persist(p).map(prod -> prod);
    }

    private Uni<Product> persistProduct(String name, String merchantId, String categoryId) {
        return persistProduct(name, merchantId, categoryId, 100, 50);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllProductRequest findAllReq(int page, int size, String search) {
        FindAllProductRequest req = new FindAllProductRequest();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    private FindAllProductByMerchantRequest findAllByMerchantReq(Long merchantId, int page, int size, String search) {
        FindAllProductByMerchantRequest req = new FindAllProductByMerchantRequest();
        req.setMerchantId("507f1f77bcf86cd799439011");
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search);
        return req;
    }

    private FindAllProductByCategoryRequest findAllByCategoryReq(String categoryName, int page, int size, String search) {
        FindAllProductByCategoryRequest req = new FindAllProductByCategoryRequest();
        req.setCategoryName(categoryName);
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindById() {
        clean()
                .chain(() -> persistProduct("Product One", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012", 5000, 20))
                .chain(p -> queryRepo.findProductById(p.getProductId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Product One");
                    assertThat(found.getMerchantId()).isEqualTo(new ObjectId("507f1f77bcf86cd799439011"));
                    assertThat(found.getCategoryId()).isEqualTo(new ObjectId("507f1f77bcf86cd799439012"));
                    assertThat(found.getPrice()).isEqualTo(5000);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> queryRepo.findProductById("507f1f77bcf86cd799439011"))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindAllProductsWithSearch() {
        clean()
                .chain(() -> persistProduct("Alpha", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("Beta", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("Gamma", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> queryRepo.findAllProducts(findAllReq(1, 10, "beta")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Beta");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindAllProductsPagination() {
        clean()
                .chain(() -> persistProduct("A", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("B", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("C", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("D", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("E", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> queryRepo.findAllProducts(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findAllProducts(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveProductsExcludesTrashed() {
        clean()
                .chain(() -> persistProduct("Active", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("ToTrash", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012")
                        .chain(p -> commandRepo.trashed(p.getProductId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveProducts(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedProductsOnlyTrashed() {
        clean()
                .chain(() -> persistProduct("Stay", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("TrashMe", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012")
                        .chain(p -> commandRepo.trashed(p.getProductId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedProducts(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("TrashMe");
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== findByMerchant ====================

    @Test
    void testFindProductsByMerchant() {
        clean()
                .chain(() -> persistProduct("P1", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("P2", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> persistProduct("P3", "507f1f77bcf86cd799439013", "507f1f77bcf86cd799439012"))
                .chain(() -> queryRepo.findProductsByMerchant(findAllByMerchantReq(100L, 1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                    assertThat(result.getData().stream()
                            .allMatch(p -> p.getMerchantId().equals(new ObjectId("507f1f77bcf86cd799439011")))).isTrue();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindProductsByMerchantWithCategoryAndPriceFilter() {
        clean()
                .chain(() -> persistProduct("Cheap", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012", 50, 100))
                .chain(() -> persistProduct("Mid",   "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012", 200, 100))
                .chain(() -> persistProduct("Exp",   "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012", 500, 100))
                .chain(() -> {
                    FindAllProductByMerchantRequest req = findAllByMerchantReq(100L, 1, 10, "");
                    req.setCategoryId("507f1f77bcf86cd799439012");
                    req.setMinPrice(100);
                    req.setMaxPrice(400);
                    return queryRepo.findProductsByMerchant(req);
                })
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Mid");
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== findByCategory ====================

    @Test
    void testFindProductsByCategory() {
        clean()
                .chain(() -> persistProduct("Phone", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))  // categoryId=50 assumed to be "Electronics"
                .chain(() -> persistProduct("Tablet", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> queryRepo.findProductsByCategory(findAllByCategoryReq("Electronics", 1, 10, "")))
                .invoke(result -> {
                    assertThat(result).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashProduct() {
        clean()
                .chain(() -> persistProduct("Trash", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(p -> commandRepo.trashed(p.getProductId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame() {
        clean()
                .chain(() -> persistProduct("Double", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(p -> commandRepo.trashed(p.getProductId())
                        .chain(() -> commandRepo.trashed(p.getProductId())))
                .invoke(second -> {
                    assertThat(second).isNotNull();
                    assertThat(second.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreProduct() {
        clean()
                .chain(() -> persistProduct("Restore", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(p -> commandRepo.trashed(p.getProductId())
                        .chain(() -> commandRepo.restore(p.getProductId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreNotTrashedReturnsNull() {
        clean()
                .chain(() -> persistProduct("ActiveR", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(p -> commandRepo.restore(p.getProductId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash() {
        clean()
                .chain(() -> persistProduct("DelPerm", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(p -> commandRepo.trashed(p.getProductId())
                        .chain(() -> commandRepo.deletePermanent(p.getProductId()))
                        .chain(perm -> queryRepo.findProductById(p.getProductId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentActiveReturnsNull() {
        clean()
                .chain(() -> persistProduct("ActiveDel", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(p -> commandRepo.deletePermanent(p.getProductId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persistProduct("A1", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012").chain(p -> commandRepo.trashed(p.getProductId()).replaceWithVoid()))
                .chain(() -> persistProduct("A2", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012").chain(p -> commandRepo.trashed(p.getProductId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedProducts(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persistProduct("D1", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012").chain(p -> commandRepo.trashed(p.getProductId()).replaceWithVoid()))
                .chain(() -> persistProduct("D2", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012").chain(p -> commandRepo.trashed(p.getProductId()).replaceWithVoid()))
                .chain(() -> persistProduct("Keep", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveProducts(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords() {
        clean()
                .chain(() -> queryRepo.findAllProducts(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveProducts(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedProducts(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testSearchNoMatchReturnsZero() {
        clean()
                .chain(() -> persistProduct("Something", "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"))
                .chain(() -> queryRepo.findAllProducts(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }
}