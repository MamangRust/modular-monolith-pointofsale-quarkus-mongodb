package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.CategoryDto;
import com.sanedge.gateway.domain.requests.FindAllCategoriesRequest;
import com.sanedge.gateway.domain.requests.GetCategoryMonthlyTotalRequest;
import com.sanedge.gateway.domain.requests.GetCategoryMonthlyTotalByMerchantRequest;
import io.smallrye.mutiny.Uni;

public interface CategoryService {
    Uni<CategoryDto.ApiResponsePaginationCategory> listCategories(FindAllCategoriesRequest request);
    Uni<CategoryDto.ApiResponseCategory> getCategory(String id);
    Uni<CategoryDto.ApiResponsePaginationCategoryDeleteAt> getActiveCategories(FindAllCategoriesRequest request);
    Uni<CategoryDto.ApiResponsePaginationCategoryDeleteAt> getTrashedCategories(FindAllCategoriesRequest request);
    Uni<CategoryDto.ApiResponseCategory> createCategory(CategoryDto.CreateRequest body);
    Uni<CategoryDto.ApiResponseCategory> updateCategory(String id, CategoryDto.UpdateRequest body);
    Uni<CategoryDto.ApiResponseCategoryDeleteAt> deleteCategory(String id);
    Uni<CategoryDto.ApiResponseCategoryDeleteAt> restoreCategory(String id);
    Uni<CategoryDto.SimpleResponse> deleteCategoryPermanent(String id);
    Uni<CategoryDto.SimpleResponse> restoreAllCategory();
    Uni<CategoryDto.SimpleResponse> deleteAllCategoryPermanent();

    // Stats
    Uni<CategoryDto.ApiResponseCategoryMonthlyTotalPrice> getMonthlyTotalPrices(int year, int month);
    Uni<CategoryDto.ApiResponseCategoryYearlyTotalPrice> getYearlyTotalPrices(int year);
    Uni<CategoryDto.ApiResponseCategoryMonthlyTotalPrice> getMonthlyTotalPricesById(GetCategoryMonthlyTotalRequest request);
    Uni<CategoryDto.ApiResponseCategoryYearlyTotalPrice> getYearlyTotalPricesById(String categoryId, int year);
    Uni<CategoryDto.ApiResponseCategoryMonthlyTotalPrice> getMonthlyTotalPricesByMerchant(GetCategoryMonthlyTotalByMerchantRequest request);
    Uni<CategoryDto.ApiResponseCategoryYearlyTotalPrice> getYearlyTotalPricesByMerchant(String merchantId, int year);
    Uni<CategoryDto.ApiResponseCategoryMonthPrice> getMonthlyPrices(int year);
    Uni<CategoryDto.ApiResponseCategoryYearPrice> getYearlyPrices(int year);
    Uni<CategoryDto.ApiResponseCategoryMonthPrice> getMonthlyPricesByMerchant(String merchantId, int year);
    Uni<CategoryDto.ApiResponseCategoryYearPrice> getYearlyPricesByMerchant(String merchantId, int year);
    Uni<CategoryDto.ApiResponseCategoryMonthPrice> getMonthlyPricesById(String categoryId, int year);
    Uni<CategoryDto.ApiResponseCategoryYearPrice> getYearlyPricesById(String categoryId, int year);
}
