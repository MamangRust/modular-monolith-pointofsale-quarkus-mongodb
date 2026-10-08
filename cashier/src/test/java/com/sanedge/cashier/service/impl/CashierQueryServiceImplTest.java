package com.sanedge.cashier.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.cashier.domain.requests.FindAllCashierMerchant;
import com.sanedge.cashier.domain.requests.FindAllCashiers;
import com.sanedge.cashier.domain.response.CashierResponse;
import com.sanedge.cashier.domain.response.CashierResponseDeleteAt;
import com.sanedge.cashier.entity.Cashier;
import com.sanedge.cashier.repository.CashierQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CashierQueryServiceImplTest {

    @Mock
    private CashierQueryRepository cashierQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CashierQueryServiceImpl cashierQueryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        cashierQueryService = new CashierQueryServiceImpl(
                cashierQueryRepository,
                redisService,
                objectMapper,
                tracingMetrics);

        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(
                        anyString(),
                        anyString(),
                        any(Attributes.class),
                        any());
    }

    private static final String CASHIER_ID = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439011";
    private static final String USER_ID = "507f1f77bcf86cd799439013";

    private Cashier createMockCashier(String id, String name, String merchantId, String userId) {
        Cashier cashier = new Cashier();
        cashier.setCashierId(id);
        cashier.setName(name);
        cashier.setMerchantId(new ObjectId(merchantId));
        cashier.setUserId(new ObjectId(userId));
        cashier.setCreatedAt(java.time.Instant.now());
        cashier.setUpdatedAt(java.time.Instant.now());
        return cashier;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize in test helper", e);
        }
    }

    @Test
    void findById_cacheHit_returnsCached() {
        Cashier cashier = createMockCashier("507f1f77bcf86cd799439011",  "Cashier1", MERCHANT_ID, USER_ID);
        CashierResponse cachedResponse = CashierResponse.from(cashier);
        String cachedJson = toJson(cachedResponse);

        when(redisService.getReactive("cashier:" + CASHIER_ID)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponse<CashierResponse> response = cashierQueryService.findById("507f1f77bcf86cd799439011").await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isNotNull();
        assertThat(response.data().getName()).isEqualTo("Cashier1");
        verify(cashierQueryRepository, never()).findByCashierId(anyString());
    }

    @Test
    void findById_cacheMiss_fetchesFromDbAndCaches() {
        Cashier cashier = createMockCashier("507f1f77bcf86cd799439011",  "Cashier2", MERCHANT_ID, USER_ID);

        when(redisService.getReactive("cashier:" + CASHIER_ID)).thenReturn(Uni.createFrom().nullItem());
        when(cashierQueryRepository.findByCashierId(CASHIER_ID)).thenReturn(Uni.createFrom().item(cashier));
        when(redisService.setReactive(eq("cashier:" + CASHIER_ID), anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<CashierResponse> response = cashierQueryService.findById(CASHIER_ID).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data().getName()).isEqualTo("Cashier2");
        verify(cashierQueryRepository).findByCashierId(CASHIER_ID);
        verify(redisService).setReactive(eq("cashier:" + CASHIER_ID), anyString());
    }

    @Test
    void findById_notFound_throwsException() {
        when(redisService.getReactive("cashier:" + CASHIER_ID)).thenReturn(Uni.createFrom().nullItem());
        when(cashierQueryRepository.findByCashierId(CASHIER_ID)).thenReturn(Uni.createFrom().nullItem());

        try {
            cashierQueryService.findById(CASHIER_ID).await().indefinitely();
            org.junit.jupiter.api.Assertions.fail("Expected exception");
        } catch (jakarta.ws.rs.NotFoundException e) {
            assertThat(e.getMessage()).contains("not found");
        }
    }

    @Test
    void findAll_cacheHit_returnsCachedList() {
        FindAllCashiers req = new FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);
        String cacheKey = "cashiers:all:1:10:null";

        CashierResponse res1 = CashierResponse.from(createMockCashier("507f1f77bcf86cd799439011",  "C1", MERCHANT_ID, USER_ID));
        ApiResponsePagination<List<CashierResponse>> mockResponse = new ApiResponsePagination<>(
                "success", "Found", List.of(res1), null);
        String cachedJson = toJson(mockResponse);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponsePagination<List<CashierResponse>> response = cashierQueryService.findAll(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        verify(cashierQueryRepository, never()).findAllCashiers(any());
    }

    @Test
    void findAll_cacheMiss_fetchesFromDbAndCaches() {
        FindAllCashiers req = new FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);
        String cacheKey = "cashiers:all:1:10:null";

        Cashier c1 = createMockCashier("507f1f77bcf86cd799439011",  "C1", MERCHANT_ID, USER_ID);
        PagedResult<Cashier> pagedResult = new PagedResult<>(List.of(c1), 1);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(cashierQueryRepository.findAllCashiers(req)).thenReturn(Uni.createFrom().item(pagedResult));
        when(redisService.setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<CashierResponse>> response = cashierQueryService.findAll(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        verify(cashierQueryRepository).findAllCashiers(req);
    }

    @Test
    void findActive_cacheHit_returnsCachedList() {
        FindAllCashiers req = new FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);
        String cacheKey = "cashiers:active:1:10:null";

        CashierResponseDeleteAt res1 = CashierResponseDeleteAt.from(createMockCashier("507f1f77bcf86cd799439011",  "Active1", MERCHANT_ID, USER_ID));
        ApiResponsePagination<List<CashierResponseDeleteAt>> mockResponse = new ApiResponsePagination<>(
                "success", "Found", List.of(res1), null);
        String cachedJson = toJson(mockResponse);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponsePagination<List<CashierResponseDeleteAt>> response = cashierQueryService.findByActive(req).await().indefinitely();

        assertThat(response.data()).hasSize(1);
        verify(cashierQueryRepository, never()).findActiveCashiers(any());
    }

    @Test
    void findTrashed_cacheHit_returnsCachedList() {
        FindAllCashiers req = new FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);
        String cacheKey = "cashiers:trashed:1:10:null";

        CashierResponseDeleteAt res1 = CashierResponseDeleteAt.from(createMockCashier("507f1f77bcf86cd799439011",  "Trashed1", MERCHANT_ID, USER_ID));
        ApiResponsePagination<List<CashierResponseDeleteAt>> mockResponse = new ApiResponsePagination<>(
                "success", "Found", List.of(res1), null);
        String cachedJson = toJson(mockResponse);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponsePagination<List<CashierResponseDeleteAt>> response = cashierQueryService.findByTrashed(req).await().indefinitely();

        assertThat(response.data()).hasSize(1);
        verify(cashierQueryRepository, never()).findTrashedCashiers(any());
    }

    @Test
    void findByMerchant_cacheHit_returnsCachedList() {
        FindAllCashierMerchant req = new FindAllCashierMerchant();
        req.setMerchantId("507f1f77bcf86cd799439011");
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);
        String cacheKey = "cashiers:merchant:" + MERCHANT_ID + ":1:10:null";

        CashierResponse res1 = CashierResponse.from(createMockCashier("507f1f77bcf86cd799439011",  "MC1", MERCHANT_ID, USER_ID));
        ApiResponsePagination<List<CashierResponse>> mockResponse = new ApiResponsePagination<>(
                "success", "Found", List.of(res1), null);
        String cachedJson = toJson(mockResponse);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponsePagination<List<CashierResponse>> response = cashierQueryService.findByMerchant(req).await().indefinitely();

        assertThat(response.data()).hasSize(1);
        verify(cashierQueryRepository, never()).findByMerchants(any());
    }
}
