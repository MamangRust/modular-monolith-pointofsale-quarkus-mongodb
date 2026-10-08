package com.sanedge.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.bson.types.ObjectId;

import org.junit.jupiter.api.Test;

import com.sanedge.common.enums.Status;
import com.sanedge.merchant.domain.requests.FindAllMerchants;
import com.sanedge.merchant.entity.Merchant;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class MerchantRepositoryTest extends MongoTestBase {

    @Inject
    MerchantQueryRepository queryRepo;

    @Inject
    MerchantCommandRepository commandRepo;

    private static final String DEFAULT_USER_ID = "507f1f77bcf86cd799439010";
    private static final String USER_42 = "5f1f77bcf86cd79943901142";
    private static final String UNKNOWN_ID = "000000000000000000000000";

    private Uni<Merchant> persistMerchant(String name, String apiKey, String userId, Status status) {
        Merchant merchant = new Merchant();
        merchant.setName(name);
        merchant.setApiKey(apiKey);
        merchant.setUserId(new ObjectId(userId != null ? userId : DEFAULT_USER_ID));
        merchant.setStatus(status != null ? status : Status.SUCCESS);
        merchant.setMerchantNo(UUID.randomUUID());
        merchant.setCreatedAt(Instant.now());
        merchant.setUpdatedAt(Instant.now());
        return queryRepo.persist(merchant).map(m -> m);
    }

    private Uni<Merchant> persistMerchant(String name, String apiKey, String userId) {
        return persistMerchant(name, apiKey, userId, Status.SUCCESS);
    }

    private Uni<Merchant> persistMerchant(String name, String apiKey) {
        return persistMerchant(name, apiKey, DEFAULT_USER_ID, Status.SUCCESS);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchants findAllReq(int page, int size, String search) {
        FindAllMerchants req = new FindAllMerchants();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindById() {
        clean()
                .chain(() -> persistMerchant("Merchant One", "key-001"))
                .chain(m -> queryRepo.findMerchantById(m.getMerchantId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Merchant One");
                    assertThat(found.getApiKey()).isEqualTo("key-001");
                    assertThat(found.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> queryRepo.findMerchantById(UNKNOWN_ID))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByApiKey() {
        clean()
                .chain(() -> persistMerchant("Api Merchant", "secret-key"))
                .chain(() -> queryRepo.findByApiKey("secret-key"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getApiKey()).isEqualTo("secret-key");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByName() {
        clean()
                .chain(() -> persistMerchant("Unique Name", "key"))
                .chain(() -> queryRepo.findByName("Unique Name"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Unique Name");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testExistsByNameTrue() {
        clean()
                .chain(() -> persistMerchant("Exists", "k1"))
                .chain(() -> queryRepo.existsByName("Exists"))
                .invoke(exists -> assertThat(exists).isTrue())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testExistsByNameFalse() {
        clean()
                .chain(() -> queryRepo.existsByName("NonExistent"))
                .invoke(exists -> assertThat(exists).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindMerchantsWithSearch() {
        clean()
                .chain(() -> persistMerchant("Alpha Store", "key-a"))
                .chain(() -> persistMerchant("Beta Shop", "key-b"))
                .chain(() -> persistMerchant("Gamma Market", "key-c"))
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 10, "beta")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Beta Shop");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantsPagination() {
        clean()
                .chain(() -> persistMerchant("A", "a"))
                .chain(() -> persistMerchant("B", "b"))
                .chain(() -> persistMerchant("C", "c"))
                .chain(() -> persistMerchant("D", "d"))
                .chain(() -> persistMerchant("E", "e"))
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findMerchants(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveMerchantsExcludesTrashed() {
        clean()
                .chain(() -> persistMerchant("Active", "a"))
                .chain(() -> persistMerchant("ToTrash", "t")
                        .chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedMerchantsOnlyTrashed() {
        clean()
                .chain(() -> persistMerchant("Stay", "s"))
                .chain(() -> persistMerchant("TrashMe", "t")
                        .chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("TrashMe");
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashMerchant() {
        clean()
                .chain(() -> persistMerchant("Trash", "tk"))
                .chain(m -> commandRepo.trashed(m.getMerchantId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashAlreadyTrashedReturnsNull() {
        clean()
                .chain(() -> persistMerchant("Double", "dk"))
                .chain(m -> commandRepo.trashed(m.getMerchantId())
                        .chain(() -> commandRepo.trashed(m.getMerchantId())))
                .invoke(second -> assertThat(second).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreMerchant() {
        clean()
                .chain(() -> persistMerchant("Restore", "rk"))
                .chain(m -> commandRepo.trashed(m.getMerchantId())
                        .chain(() -> commandRepo.restore(m.getMerchantId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreNotTrashedReturnsNull() {
        clean()
                .chain(() -> persistMerchant("ActiveR", "ark"))
                .chain(m -> commandRepo.restore(m.getMerchantId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash() {
        clean()
                .chain(() -> persistMerchant("DelPerm", "dp"))
                .chain(m -> commandRepo.trashed(m.getMerchantId())
                        .chain(() -> commandRepo.deletePermanent(m.getMerchantId()))
                        .chain(success -> queryRepo.findMerchantById(m.getMerchantId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentActiveReturnsFalse() {
        clean()
                .chain(() -> persistMerchant("ActiveDel", "ad"))
                .chain(m -> commandRepo.deletePermanent(m.getMerchantId()))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persistMerchant("A1", "a1").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persistMerchant("A2", "a2").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persistMerchant("D1", "d1").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persistMerchant("D2", "d2").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persistMerchant("Keep", "keep"))
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Update Status ====================

    @Test
    void testUpdateStatusToSuspended() {
        clean()
                .chain(() -> persistMerchant("Status", "st", DEFAULT_USER_ID, Status.SUCCESS))
                .chain(m -> commandRepo.updateStatus(m.getMerchantId(), "FAILED")
                        .chain(() -> queryRepo.findMerchantById(m.getMerchantId())))
                .invoke(updated -> assertThat(updated.getStatus()).isEqualTo(Status.FAILED))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testUpdateStatusInvalid() {
        clean()
                .chain(() -> persistMerchant("Invalid", "inv"))
                .chain(m -> commandRepo.updateStatus(m.getMerchantId(), "INVALID"))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== findByUserId ====================

    @Test
    void testFindByUserId() {
        clean()
                .chain(() -> persistMerchant("U1", "u1", USER_42))
                .chain(() -> persistMerchant("U2", "u2", USER_42))
                .chain(() -> queryRepo.findByUserId(USER_42))
                .invoke(list -> assertThat(list).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByUserIdNullReturnsEmpty() {
        clean()
                .chain(() -> queryRepo.findByUserId(null))
                .invoke(list -> assertThat(list).isEmpty())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords() {
        clean()
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testSearchNoMatchReturnsZero() {
        clean()
                .chain(() -> persistMerchant("Something", "sm"))
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }
}