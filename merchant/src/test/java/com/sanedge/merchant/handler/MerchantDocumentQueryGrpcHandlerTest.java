package com.sanedge.merchant.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.merchant.domain.response.MerchantDocumentResponse;
import com.sanedge.merchant.domain.response.MerchantDocumentResponseDeleteAt;
import com.sanedge.merchant.service.MerchantDocumentQueryService;

import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.NotFoundException;
import pb.merchant_document.MerchantDocumentOuterClass;
import pb.merchant_document.MerchantDocumentQuery;

@ExtendWith(MockitoExtension.class)
class MerchantDocumentQueryGrpcHandlerTest {

    @Mock
    private MerchantDocumentQueryService documentQueryService;

    private MerchantDocumentQueryGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MerchantDocumentQueryGrpcHandler();
        handler.merchantDocumentQueryService = documentQueryService;
    }

    // helpers
    private static final String DOC_ID = "507f1f77bcf86cd799439021";
    private static final String DOC_ID_2 = "507f1f77bcf86cd799439022";
    private static final String DOC_ID_3 = "507f1f77bcf86cd799439023";
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439010";
    private static final String UNKNOWN_ID = "ffffffffffffffffffffffff";

    private MerchantDocumentResponse createDocResponse(String docId) {
        MerchantDocumentResponse r = new MerchantDocumentResponse();
        r.setDocumentId(docId);
        r.setMerchantId(MERCHANT_ID);
        r.setDocumentType("ID_CARD");
        r.setDocumentUrl("http://docs.com/id.jpg");
        r.setStatus("PENDING");
        r.setNote("test note");
        r.setCreatedAt(LocalDateTime.of(2024, 6, 15, 10, 30).toString());
        r.setUpdatedAt(LocalDateTime.of(2024, 6, 15, 10, 30).toString());
        return r;
    }

    private MerchantDocumentResponseDeleteAt createDocDeleteAtResponse(String docId) {
        MerchantDocumentResponseDeleteAt r = new MerchantDocumentResponseDeleteAt();
        r.setDocumentId(docId);
        r.setMerchantId(MERCHANT_ID);
        r.setDocumentType("ID_CARD");
        r.setDocumentUrl("http://docs.com/id.jpg");
        r.setStatus("INACTIVE");
        r.setNote("trashed");
        r.setCreatedAt(LocalDateTime.of(2024, 6, 15, 10, 30).toString());
        r.setUpdatedAt(LocalDateTime.of(2024, 6, 15, 10, 30).toString());
        r.setDeletedAt(LocalDateTime.of(2024, 6, 16, 8, 0).toString());
        return r;
    }

    private MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest findAllReq() {
        return MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .setSearch("")
                .build();
    }

    // findAll
    @Test
    @DisplayName("findAll - success with data")
    void findAll_Success() {
        MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest request = findAllReq();
        MerchantDocumentResponse data = createDocResponse(DOC_ID);
        ApiResponsePagination<List<MerchantDocumentResponse>> apiResp = new ApiResponsePagination<>(
                "success", "Documents retrieved", List.of(data), null);
        when(documentQueryService.findAll(any())).thenReturn(Uni.createFrom().item(apiResp));

        MerchantDocumentQuery.ApiResponsePaginationMerchantDocument response = handler.findAll(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).getDocumentId()).isEqualTo(DOC_ID);
        assertThat(response.getData(0).getDocumentType()).isEqualTo("ID_CARD");
    }

    @Test
    @DisplayName("findAll - internal error")
    void findAll_Error() {
        when(documentQueryService.findAll(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));
        try {
            handler.findAll(findAllReq()).await().indefinitely();
        } catch (StatusRuntimeException e) {
            assertThat(e).isNotNull();
        }
    }

    // findAllActive
    @Test
    @DisplayName("findAllActive - success")
    void findAllActive_Success() {
        MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest request = findAllReq();
        MerchantDocumentResponseDeleteAt data = createDocDeleteAtResponse(DOC_ID_2);
        ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>> apiResp = new ApiResponsePagination<>(
                "success", "Active documents", List.of(data), null);
        when(documentQueryService.findAllActive(any())).thenReturn(Uni.createFrom().item(apiResp));

        MerchantDocumentQuery.ApiResponsePaginationMerchantDocumentAt response = handler.findAllActive(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).hasDeletedAt()).isTrue();
    }

    @Test
    @DisplayName("findAllActive - internal error")
    void findAllActive_Error() {
        when(documentQueryService.findAllActive(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));
        try {
            handler.findAllActive(findAllReq()).await().indefinitely();
        } catch (StatusRuntimeException e) {
            assertThat(e).isNotNull();
        }
    }

    // findAllTrashed
    @Test
    @DisplayName("findAllTrashed - success")
    void findAllTrashed_Success() {
        MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest request = findAllReq();
        MerchantDocumentResponseDeleteAt data = createDocDeleteAtResponse(DOC_ID_3);
        ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>> apiResp = new ApiResponsePagination<>(
                "success", "Trashed documents", List.of(data), null);
        when(documentQueryService.findAllTrashed(any())).thenReturn(Uni.createFrom().item(apiResp));

        MerchantDocumentQuery.ApiResponsePaginationMerchantDocumentAt response = handler.findAllTrashed(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).hasDeletedAt()).isTrue();
    }

    @Test
    @DisplayName("findAllTrashed - internal error")
    void findAllTrashed_Error() {
        when(documentQueryService.findAllTrashed(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));
        try {
            handler.findAllTrashed(findAllReq()).await().indefinitely();
        } catch (StatusRuntimeException e) {
            assertThat(e).isNotNull();
        }
    }

    // findById
    @Test
    @DisplayName("findById - success")
    void findById_Success() {
        MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest request = MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                .setDocumentId(DOC_ID).build();

        MerchantDocumentResponse data = createDocResponse(DOC_ID);
        ApiResponse<MerchantDocumentResponse> apiResp = ApiResponse.success("Document found", data);
        when(documentQueryService.findById(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        MerchantDocumentOuterClass.ApiResponseMerchantDocument response = handler.findById(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getDocumentId()).isEqualTo(DOC_ID);
    }

    @Test
    @DisplayName("findById - NOT_FOUND when NotFoundException thrown")
    void findById_NotFound() {
        MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest request = MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                .setDocumentId(UNKNOWN_ID).build();

        when(documentQueryService.findById(anyString()))
                .thenReturn(Uni.createFrom().failure(new NotFoundException("Document not found")));

        try {
            handler.findById(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("findById - internal error on other exception")
    void findById_InternalError() {
        MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest request = MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                .setDocumentId(DOC_ID).build();

        when(documentQueryService.findById(anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Unexpected")));

        try {
            handler.findById(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("findAll - empty list")
    void findAll_EmptyList() {
        when(documentQueryService.findAll(any()))
                .thenReturn(Uni.createFrom().item(new ApiResponsePagination<>("success", "No documents", List.of(), null)));

        MerchantDocumentQuery.ApiResponsePaginationMerchantDocument response = handler.findAll(findAllReq()).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isZero();
    }

    @Test
    @DisplayName("findById - null data in response")
    void findById_NullData() {
        MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest request = MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                .setDocumentId(DOC_ID).build();

        when(documentQueryService.findById(anyString()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("No data", null)));

        MerchantDocumentOuterClass.ApiResponseMerchantDocument response = handler.findById(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.hasData()).isFalse();
    }
}