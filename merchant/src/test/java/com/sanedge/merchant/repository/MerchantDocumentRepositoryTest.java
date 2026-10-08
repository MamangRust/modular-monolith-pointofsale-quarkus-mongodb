package com.sanedge.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.merchant.domain.requests.FindAllMerchantDocuments;
import com.sanedge.merchant.entity.MerchantDocument;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class MerchantDocumentRepositoryTest extends MongoTestBase {

    @Inject
    MerchantDocumentQueryRepository queryRepo;

    @Inject
    MerchantDocumentCommandRepository commandRepo;

    // ---------- helpers ----------
    private static final String UNKNOWN_ID = "000000000000000000000000";

    private Uni<MerchantDocument> persistDocument(Integer merchantId, String docType, String docUrl, String status, String note) {
        MerchantDocument doc = new MerchantDocument();
        doc.setMerchantId(new ObjectId(String.format("%024x", merchantId)));
        doc.setDocumentType(docType);
        doc.setDocumentUrl(docUrl);
        doc.setStatus(status);
        doc.setNote(note);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return queryRepo.persist(doc).map(d -> d);
    }

    private Uni<MerchantDocument> persistDocument(Integer merchantId) {
        return persistDocument(merchantId, "ID_CARD", "http://docs.com/id.jpg", "PENDING", null);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchantDocuments findAllReq(int page, int size, String search) {
        FindAllMerchantDocuments req = new FindAllMerchantDocuments();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindById() {
        clean()
                .chain(() -> persistDocument(1, "PASSPORT", "http://doc.com/pass.pdf", "APPROVED", "verified"))
                .chain(d -> queryRepo.findDocumentById(d.getDocumentId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getDocumentType()).isEqualTo("PASSPORT");
                    assertThat(found.getStatus()).isEqualTo("APPROVED");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> queryRepo.findDocumentById(UNKNOWN_ID))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindDocumentsWithSearch() {
        clean()
                .chain(() -> persistDocument(1, "ID_CARD", "url1", "PENDING", "note1"))
                .chain(() -> persistDocument(1, "BUSINESS_LICENSE", "url2", "APPROVED", "note2"))
                .chain(() -> persistDocument(2, "TAX_DOC", "url3", "PENDING", "note3"))
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 10, "BUSINESS")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getDocumentType()).isEqualTo("BUSINESS_LICENSE");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindDocumentsPagination() {
        clean()
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findDocuments(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveDocumentsExcludesTrashed() {
        clean()
                .chain(() -> persistDocument(10))
                .chain(() -> persistDocument(10).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveDocuments(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedDocumentsOnlyTrashed() {
        clean()
                .chain(() -> persistDocument(20))
                .chain(() -> persistDocument(20).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedDocuments(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashDocument() {
        clean()
                .chain(() -> persistDocument(30))
                .chain(d -> commandRepo.trashed(d.getDocumentId()))
                .invoke(trashed -> assertThat(trashed.getDeletedAt()).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashAlreadyTrashedReturnsNull() {
        clean()
                .chain(() -> persistDocument(31))
                .chain(d -> commandRepo.trashed(d.getDocumentId())
                        .chain(() -> commandRepo.trashed(d.getDocumentId())))
                .invoke(second -> assertThat(second).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreDocument() {
        clean()
                .chain(() -> persistDocument(32))
                .chain(d -> commandRepo.trashed(d.getDocumentId())
                        .chain(() -> commandRepo.restore(d.getDocumentId())))
                .invoke(restored -> assertThat(restored.getDeletedAt()).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreNotTrashedReturnsNull() {
        clean()
                .chain(() -> persistDocument(33))
                .chain(d -> commandRepo.restore(d.getDocumentId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash() {
        clean()
                .chain(() -> persistDocument(34))
                .chain(d -> commandRepo.trashed(d.getDocumentId())
                        .chain(() -> commandRepo.deletePermanent(d.getDocumentId()))
                        .chain(success -> queryRepo.findDocumentById(d.getDocumentId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentActiveReturnsFalse() {
        clean()
                .chain(() -> persistDocument(35))
                .chain(d -> commandRepo.deletePermanent(d.getDocumentId()))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persistDocument(1).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> persistDocument(2).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedDocuments(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persistDocument(1).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> persistDocument(2).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> persistDocument(3))
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveDocuments(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords() {
        clean()
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveDocuments(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedDocuments(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testSearchNoMatchReturnsZero() {
        clean()
                .chain(() -> persistDocument(99))
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid().await().indefinitely();
    }
}