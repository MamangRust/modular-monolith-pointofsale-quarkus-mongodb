package com.sanedge.cashier.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindAllCashierMerchant;
import com.sanedge.cashier.domain.requests.FindAllCashiers;
import com.sanedge.cashier.entity.Cashier;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CashierRepositoryTest extends MongoTestBase {

    private static final String MERCHANT_A = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_B = "507f1f77bcf86cd799439012";
    private static final String USER_A = "507f1f77bcf86cd799439013";
    private static final String MISSING_ID = "507f1f77bcf86cd799439099";

    @Inject
    CashierCommandRepository cashierCommandRepository;

    @Inject
    CashierQueryRepository cashierQueryRepository;

    // ---------- helpers ----------
    private Uni<Cashier> persistCashier(String name, String merchantId, String userId) {
        Cashier cashier = new Cashier();
        cashier.setName(name);
        cashier.setMerchantId(new ObjectId(merchantId));
        cashier.setUserId(new ObjectId(userId));
        cashier.setCreatedAt(Instant.now());
        cashier.setUpdatedAt(Instant.now());
        return cashierCommandRepository.persist(cashier).replaceWith(cashier);
    }

    private Uni<Cashier> persistCashier(String name, String merchantId) {
        return persistCashier(name, merchantId, USER_A);
    }

    private Uni<Void> clean() {
        return cashierCommandRepository.deleteAll().replaceWithVoid();
    }

    private FindAllCashiers findAllReq(int page, int size, String search) {
        FindAllCashiers req = new FindAllCashiers();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    private FindAllCashierMerchant findByMerchantReq(String merchantId, int page, int size, String search) {
        FindAllCashierMerchant req = new FindAllCashierMerchant();
        req.setMerchantId(merchantId);
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Create & Find ====================

    @Test
    void testCreateCashier_success() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .invoke(persisted -> {
                    assertThat(persisted).isNotNull();
                    assertThat(persisted.getCashierId()).isNotNull();
                    assertThat(persisted.getName()).isEqualTo("Cashier One");
                    assertThat(persisted.getMerchantId()).isEqualTo(new ObjectId(MERCHANT_A));
                    assertThat(persisted.getUserId()).isEqualTo(new ObjectId(USER_A));
                    assertThat(persisted.getCreatedAt()).isNotNull();
                    assertThat(persisted.getUpdatedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByCashierId_exists() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(cashier -> cashierQueryRepository.findByCashierId(cashier.getCashierId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByCashierId_notFound() {
        clean()
                .chain(() -> cashierQueryRepository.findByCashierId(MISSING_ID))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByNameAndMerchantId_exists() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(() -> cashierQueryRepository.findByNameAndMerchantId("Cashier One", MERCHANT_A))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByNameAndMerchantId_differentMerchantNotFound() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(() -> cashierQueryRepository.findByNameAndMerchantId("Cashier One", MERCHANT_B))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByNameAndMerchantId_notFound() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(() -> cashierQueryRepository.findByNameAndMerchantId("NonExistent", MERCHANT_A))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindAllCashiers_paginationAndSearch() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(() -> persistCashier("Cashier Two", MERCHANT_B))
                .chain(() -> cashierQueryRepository.findAllCashiers(findAllReq(1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(2);
                    assertThat(pagedResult.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindAllCashiers_withSearchKeyword() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(() -> persistCashier("Cashier Two", MERCHANT_B))
                .chain(() -> cashierQueryRepository.findAllCashiers(findAllReq(1, 10, "One")))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveCashiers_onlyActive() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A)
                        .chain(c -> cashierCommandRepository.trashed(c.getCashierId()).replaceWithVoid()))
                .chain(() -> persistCashier("Cashier Two", MERCHANT_B))
                .chain(() -> cashierQueryRepository.findActiveCashiers(findAllReq(1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier Two");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedCashiers_onlyTrashed() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A)
                        .chain(c -> cashierCommandRepository.trashed(c.getCashierId()).replaceWithVoid()))
                .chain(() -> persistCashier("Cashier Two", MERCHANT_B))
                .chain(() -> cashierQueryRepository.findTrashedCashiers(findAllReq(1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByMerchants_withMerchantAndSearch() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(() -> persistCashier("Cashier Two", MERCHANT_B))
                .chain(() -> cashierQueryRepository.findByMerchants(findByMerchantReq(MERCHANT_A, 1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashCashier_success() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(c -> cashierCommandRepository.trashed(c.getCashierId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashCashier_alreadyTrashed_returnsSame() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(c -> cashierCommandRepository.trashed(c.getCashierId())
                        .chain(() -> cashierCommandRepository.trashed(c.getCashierId())))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreCashier_success() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(c -> cashierCommandRepository.trashed(c.getCashierId())
                        .chain(() -> cashierCommandRepository.restore(c.getCashierId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreCashier_notFound() {
        clean()
                .chain(() -> cashierCommandRepository.restore(MISSING_ID))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentAfterTrash() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(c -> cashierCommandRepository.trashed(c.getCashierId())
                        .chain(() -> cashierCommandRepository.deletePermanent(c.getCashierId()))
                        .chain(() -> cashierQueryRepository.findByCashierId(c.getCashierId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted_success() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A)
                        .chain(c -> cashierCommandRepository.trashed(c.getCashierId()).replaceWithVoid()))
                .chain(() -> persistCashier("Cashier Two", MERCHANT_B)
                        .chain(c -> cashierCommandRepository.trashed(c.getCashierId()).replaceWithVoid()))
                .chain(() -> cashierCommandRepository.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> cashierQueryRepository.findTrashedCashiers(findAllReq(1, 10, null)))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted_whenNoneTrashed_returnsFalse() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A))
                .chain(() -> cashierCommandRepository.restoreAllDeleted())
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persistCashier("Cashier One", MERCHANT_A)
                        .chain(c -> cashierCommandRepository.trashed(c.getCashierId()).replaceWithVoid()))
                .chain(() -> persistCashier("Cashier Two", MERCHANT_B))
                .chain(() -> cashierCommandRepository.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> cashierQueryRepository.findActiveCashiers(findAllReq(1, 10, null)))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }
}
