package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.MerchantDto;
import com.sanedge.gateway.domain.requests.FindAllMerchantsRequest;
import io.smallrye.mutiny.Uni;

public interface MerchantService {
    Uni<MerchantDto.ApiResponsePaginationMerchant> listMerchants(FindAllMerchantsRequest request);
    Uni<MerchantDto.ApiResponseMerchant> getMerchant(String id);
    Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getActiveMerchants(FindAllMerchantsRequest request);
    Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getTrashedMerchants(FindAllMerchantsRequest request);
    Uni<MerchantDto.ApiResponseMerchant> createMerchant(MerchantDto.CreateRequest body);
    Uni<MerchantDto.ApiResponseMerchant> updateMerchant(String id, MerchantDto.UpdateRequest body);
    Uni<MerchantDto.ApiResponseMerchantDeleteAt> deleteMerchant(String id);
    Uni<MerchantDto.ApiResponseMerchantDeleteAt> restoreMerchant(String id);
    Uni<MerchantDto.SimpleResponse> deleteMerchantPermanent(String id);
    Uni<MerchantDto.SimpleResponse> restoreAllMerchant();
    Uni<MerchantDto.SimpleResponse> deleteAllMerchantPermanent();
}
