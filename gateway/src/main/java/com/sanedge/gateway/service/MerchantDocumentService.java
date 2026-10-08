package com.sanedge.gateway.service;

import com.sanedge.gateway.domain.requests.FindAllMerchantDocumentsRequest;
import com.sanedge.gateway.dto.MerchantDocumentDto;
import io.smallrye.mutiny.Uni;

public interface MerchantDocumentService {
    Uni<MerchantDocumentDto.ApiResponsePaginationMerchantDocument> listMerchantDocuments(FindAllMerchantDocumentsRequest request);
    Uni<MerchantDocumentDto.ApiResponsePaginationMerchantDocumentAt> listActiveMerchantDocuments(FindAllMerchantDocumentsRequest request);
    Uni<MerchantDocumentDto.ApiResponsePaginationMerchantDocumentAt> listTrashedMerchantDocuments(FindAllMerchantDocumentsRequest request);
    Uni<MerchantDocumentDto.ApiResponseMerchantDocument> getMerchantDocument(String id);
    Uni<MerchantDocumentDto.ApiResponseMerchantDocument> createMerchantDocument(MerchantDocumentDto.CreateRequest body);
    Uni<MerchantDocumentDto.ApiResponseMerchantDocument> updateMerchantDocument(String id, MerchantDocumentDto.UpdateRequest body);
    Uni<MerchantDocumentDto.ApiResponseMerchantDocument> updateMerchantDocumentStatus(String id, MerchantDocumentDto.UpdateStatusRequest body);
    Uni<MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt> deleteMerchantDocument(String id);
    Uni<MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt> restoreMerchantDocument(String id);
    Uni<MerchantDocumentDto.SimpleResponse> deleteMerchantDocumentPermanent(String id);
    Uni<MerchantDocumentDto.SimpleResponse> restoreAllMerchantDocuments();
    Uni<MerchantDocumentDto.SimpleResponse> deleteAllMerchantDocuments();
}
