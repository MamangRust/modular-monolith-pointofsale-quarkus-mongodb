package com.sanedge.merchant.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.bson.types.ObjectId;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant.domain.requests.CreateMerchantDocumentRequest;
import com.sanedge.merchant.domain.requests.UpdateMerchantDocumentRequest;
import com.sanedge.merchant.domain.requests.UpdateMerchantDocumentStatus;
import com.sanedge.merchant.domain.response.MerchantDocumentResponse;
import com.sanedge.merchant.domain.response.MerchantDocumentResponseDeleteAt;
import com.sanedge.merchant.entity.Merchant;
import com.sanedge.merchant.entity.MerchantDocument;
import com.sanedge.merchant.repository.MerchantDocumentCommandRepository;
import com.sanedge.merchant.repository.MerchantDocumentQueryRepository;
import com.sanedge.merchant.repository.MerchantQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class MerchantDocumentCommandServiceImplTest {

    @Mock private MerchantQueryRepository merchantQueryRepo;
    @Mock private MerchantDocumentQueryRepository documentQueryRepo;
    @Mock private MerchantDocumentCommandRepository documentCommandRepo;
    @Mock private RedisService redisService;
    @Mock private TracingMetrics tracingMetrics;

    private MerchantDocumentCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantDocumentCommandServiceImpl(
                merchantQueryRepo, documentQueryRepo, documentCommandRepo, redisService, tracingMetrics);

        // Lenient stubs for traceAndMeasure to execute the supplier directly
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());
        lenient().when(documentCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));
        lenient().when(documentCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));
    }

    // ---------- helpers ----------
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439010";
    private static final String DOC_ID = "507f1f77bcf86cd799439021";
    private static final String UNKNOWN_ID = "ffffffffffffffffffffffff";

    private Merchant createMockMerchant(String id) {
        Merchant m = new Merchant();
        m.id = new ObjectId(id);
        m.setName("Test Merchant");
        m.setApiKey("key");
        m.setUserId(new ObjectId(MERCHANT_ID));
        return m;
    }

    private MerchantDocument createMockDocument(String id, String merchantId, String status) {
        MerchantDocument doc = new MerchantDocument();
        doc.id = new ObjectId(id);
        doc.setMerchantId(new ObjectId(merchantId));
        doc.setDocumentType("ID_CARD");
        doc.setDocumentUrl("http://docs.com/id.jpg");
        doc.setStatus(status);
        doc.setNote("note");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }

    private MerchantDocument createMockDocument(String id, String merchantId) {
        return createMockDocument(id, merchantId, "PENDING");
    }

    // ---------- request builders ----------
    private CreateMerchantDocumentRequest createReq(String merchantId) {
        CreateMerchantDocumentRequest req = new CreateMerchantDocumentRequest();
        req.setMerchantId(merchantId);
        req.setDocumentType("ID_CARD");
        req.setDocumentUrl("http://docs.com/id.jpg");
        return req;
    }

    private UpdateMerchantDocumentRequest updateReq(String docId, String merchantId) {
        UpdateMerchantDocumentRequest req = new UpdateMerchantDocumentRequest();
        req.setDocumentId(docId);
        req.setMerchantId(merchantId);
        req.setDocumentType("PASSPORT");
        req.setDocumentUrl("http://docs.com/passport.jpg");
        req.setStatus("APPROVED");
        req.setNote("updated");
        return req;
    }

    private UpdateMerchantDocumentStatus updateStatusReq(String docId, String merchantId) {
        UpdateMerchantDocumentStatus req = new UpdateMerchantDocumentStatus();
        req.setDocumentId(docId);
        req.setMerchantId(merchantId);
        req.setStatus("APPROVED");
        req.setNote("status updated");
        return req;
    }

    // ========== create ==========
    @Nested
    @DisplayName("create tests")
    class CreateTests {
        @Test void success() {
            CreateMerchantDocumentRequest req = createReq(MERCHANT_ID);
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().item(createMockMerchant(MERCHANT_ID)));

            MerchantDocument saved = createMockDocument(DOC_ID, MERCHANT_ID);
            saved.setStatus("PENDING");
            when(documentCommandRepo.persist(any(MerchantDocument.class))).thenReturn(Uni.createFrom().item(saved));

            ApiResponse<MerchantDocumentResponse> resp = service.create(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDocumentId()).isEqualTo(DOC_ID);
            assertThat(resp.data().getDocumentType()).isEqualTo("ID_CARD");
        }

        @Test void merchantNotFound_returnsError() {
            CreateMerchantDocumentRequest req = createReq(UNKNOWN_ID);
            when(merchantQueryRepo.findMerchantById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantDocumentResponse> resp = service.create(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }

        @Test void failure_returnsError() {
            CreateMerchantDocumentRequest req = createReq(MERCHANT_ID);
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().item(createMockMerchant(MERCHANT_ID)));
            when(documentCommandRepo.persist(any(MerchantDocument.class))).thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

            ApiResponse<MerchantDocumentResponse> resp = service.create(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Failed to create merchant document");
        }
    }

    // ========== update ==========
    @Nested
    @DisplayName("update tests")
    class UpdateTests {
        @Test void success() {
            UpdateMerchantDocumentRequest req = updateReq(DOC_ID, MERCHANT_ID);
            when(documentQueryRepo.findDocumentById(DOC_ID)).thenReturn(Uni.createFrom().item(createMockDocument(DOC_ID, MERCHANT_ID)));
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().item(createMockMerchant(MERCHANT_ID)));

            MerchantDocument saved = createMockDocument(DOC_ID, MERCHANT_ID);
            saved.setDocumentType("PASSPORT");
            saved.setStatus("APPROVED");
            when(documentCommandRepo.persist(any(MerchantDocument.class))).thenReturn(Uni.createFrom().item(saved));

            ApiResponse<MerchantDocumentResponse> resp = service.update(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDocumentType()).isEqualTo("PASSPORT");
        }

        @Test void documentNotFound_returnsError() {
            UpdateMerchantDocumentRequest req = updateReq(UNKNOWN_ID, MERCHANT_ID);
            when(documentQueryRepo.findDocumentById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantDocumentResponse> resp = service.update(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant document not found");
        }

        @Test void merchantNotFound_returnsError() {
            UpdateMerchantDocumentRequest req = updateReq(DOC_ID, UNKNOWN_ID);
            when(documentQueryRepo.findDocumentById(DOC_ID)).thenReturn(Uni.createFrom().item(createMockDocument(DOC_ID, MERCHANT_ID)));
            when(merchantQueryRepo.findMerchantById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantDocumentResponse> resp = service.update(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }
    }

    // ========== updateStatus ==========
    @Nested
    @DisplayName("updateStatus tests")
    class UpdateStatusTests {
        @Test void success() {
            UpdateMerchantDocumentStatus req = updateStatusReq(DOC_ID, MERCHANT_ID);
            when(documentQueryRepo.findDocumentById(DOC_ID)).thenReturn(Uni.createFrom().item(createMockDocument(DOC_ID, MERCHANT_ID)));
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().item(createMockMerchant(MERCHANT_ID)));

            MerchantDocument saved = createMockDocument(DOC_ID, MERCHANT_ID, "APPROVED");
            when(documentCommandRepo.persist(any(MerchantDocument.class))).thenReturn(Uni.createFrom().item(saved));

            ApiResponse<MerchantDocumentResponse> resp = service.updateStatus(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getStatus()).isEqualTo("APPROVED");
        }

        @Test void documentNotFound_returnsError() {
            UpdateMerchantDocumentStatus req = updateStatusReq(UNKNOWN_ID, MERCHANT_ID);
            when(documentQueryRepo.findDocumentById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantDocumentResponse> resp = service.updateStatus(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant document not found");
        }

        @Test void merchantNotFound_returnsError() {
            UpdateMerchantDocumentStatus req = updateStatusReq(DOC_ID, UNKNOWN_ID);
            when(documentQueryRepo.findDocumentById(DOC_ID)).thenReturn(Uni.createFrom().item(createMockDocument(DOC_ID, MERCHANT_ID)));
            when(merchantQueryRepo.findMerchantById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantDocumentResponse> resp = service.updateStatus(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }
    }

    // ========== trash ==========
    @Nested
    @DisplayName("trash tests")
    class TrashTests {
        @Test void success() {
            MerchantDocument trashed = createMockDocument(DOC_ID, MERCHANT_ID);
            trashed.setDeletedAt(Instant.now());
            when(documentCommandRepo.trashed(DOC_ID)).thenReturn(Uni.createFrom().item(trashed));

            ApiResponse<MerchantDocumentResponseDeleteAt> resp = service.trash(DOC_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNotNull();
        }

        @Test void notFound_returnsError() {
            when(documentCommandRepo.trashed(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantDocumentResponseDeleteAt> resp = service.trash(DOC_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant document not found");
        }
    }

    // ========== restore ==========
    @Nested
    @DisplayName("restore tests")
    class RestoreTests {
        @Test void success() {
            when(documentCommandRepo.restore(DOC_ID)).thenReturn(Uni.createFrom().item(createMockDocument(DOC_ID, MERCHANT_ID)));

            ApiResponse<MerchantDocumentResponseDeleteAt> resp = service.restore(DOC_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNull();
        }

        @Test void notFound_returnsError() {
            when(documentCommandRepo.restore(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantDocumentResponseDeleteAt> resp = service.restore(DOC_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant document not found");
        }
    }

    // ========== deletePermanent ==========
    @Nested
    @DisplayName("deletePermanent tests")
    class DeletePermanentTests {
        @Test void success() {
            when(documentCommandRepo.deletePermanent(DOC_ID)).thenReturn(Uni.createFrom().item(true));

            ApiResponse<Boolean> resp = service.deletePermanent(DOC_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test void notFound_returnsError() {
            when(documentCommandRepo.deletePermanent(anyString())).thenReturn(Uni.createFrom().item(false));

            ApiResponse<Boolean> resp = service.deletePermanent(DOC_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant document not found");
        }
    }

    // ========== restoreAll ==========
    @Nested
    @DisplayName("restoreAll tests")
    class RestoreAllTests {
        @Test void success() {
            when(documentCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));
            ApiResponse<Boolean> resp = service.restoreAll().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test void failure_returnsError() {
            when(documentCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));
            ApiResponse<Boolean> resp = service.restoreAll().await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.data()).isFalse();
        }
    }

    // ========== deleteAllPermanent ==========
    @Nested
    @DisplayName("deleteAllPermanent tests")
    class DeleteAllPermanentTests {
        @Test void success_whenDeletedExist() {
            when(documentCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));
            ApiResponse<Boolean> resp = service.deleteAllPermanent().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test void noTrashed_throwsException() {
            when(documentCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.deleteAllPermanent().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed merchant documents");
        }

        @Test void failure_returnsError() {
            when(documentCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));
            // Note: the method does not catch exceptions, so it propagates.
            assertThatThrownBy(() -> service.deleteAllPermanent().await().indefinitely())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB error");
        }
    }
}