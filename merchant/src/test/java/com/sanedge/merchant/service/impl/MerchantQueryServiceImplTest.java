package com.sanedge.merchant.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.bson.types.ObjectId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.enums.Status;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant.domain.requests.FindAllMerchants;
import com.sanedge.merchant.domain.response.MerchantResponse;
import com.sanedge.merchant.domain.response.MerchantResponseDeleteAt;
import com.sanedge.merchant.entity.Merchant;
import com.sanedge.merchant.repository.MerchantQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
class MerchantQueryServiceImplTest {

    @Mock
    private MerchantQueryRepository merchantQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private MerchantQueryServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MerchantQueryServiceImpl(
                merchantQueryRepository,
                redisService,
                objectMapper,
                tracingMetrics);

        // Lenient stubs to execute the supplier directly
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    private static final String MERCHANT_ID = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_ID_2 = "507f1f77bcf86cd799439012";
    private static final String UNKNOWN_ID = "ffffffffffffffffffffffff";
    private static final String USER_ID = "507f1f77bcf86cd799439010";

    private Merchant createMockMerchant(String id) {
        return createMockMerchant(id, "Test Merchant", "test-api-key");
    }

    private Merchant createMockMerchant(String id, String apiKey) {
        return createMockMerchant(id, "Test Merchant", apiKey);
    }

    private Merchant createMockMerchant(String id, String name, String apiKey) {
        Merchant m = new Merchant();
        m.id = new ObjectId(id);
        m.setName(name);
        m.setApiKey(apiKey);
        m.setUserId(new ObjectId(USER_ID));
        m.setStatus(Status.SUCCESS);
        m.setMerchantNo(UUID.randomUUID());
        m.setCreatedAt(Instant.now());
        m.setUpdatedAt(Instant.now());
        return m;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private FindAllMerchants findAllReq(int page, int size, String search) {
        FindAllMerchants req = new FindAllMerchants();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    @Nested
    @DisplayName("findAll tests")
    class FindAllTests {
        @Test
        void cacheMiss_fetchesFromDb() {
            FindAllMerchants req = findAllReq(1, 10, "");
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findMerchants(any(FindAllMerchants.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(createMockMerchant(MERCHANT_ID)), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantResponse>> result = service.findAll(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getName()).isEqualTo("Test Merchant");
        }

        @Test
        void cacheHit_returnsCached() {
            FindAllMerchants req = findAllReq(1, 10, "");
            MerchantResponse cachedData = MerchantResponse.from(createMockMerchant(MERCHANT_ID));
            ApiResponsePagination<List<MerchantResponse>> cached = new ApiResponsePagination<>(
                    "success", "Merchants retrieved successfully", List.of(cachedData), null);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponsePagination<List<MerchantResponse>> result = service.findAll(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByActive tests")
    class FindByActiveTests {
        @Test
        void cacheMiss_fetchesFromDb() {
            FindAllMerchants req = findAllReq(1, 10, "");
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findActiveMerchants(any(FindAllMerchants.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(createMockMerchant(MERCHANT_ID)), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantResponseDeleteAt>> result = service.findByActive(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByTrashed tests")
    class FindByTrashedTests {
        @Test
        void cacheMiss_fetchesFromDb() {
            FindAllMerchants req = findAllReq(1, 10, "");
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findTrashedMerchants(any(FindAllMerchants.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(createMockMerchant(MERCHANT_ID)), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantResponseDeleteAt>> result = service.findByTrashed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findById tests")
    class FindByIdTests {
        @Test
        void cacheMiss_fetchesFromDb() {
            String id = MERCHANT_ID;
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findMerchantById(anyString()))
                    .thenReturn(Uni.createFrom().item(createMockMerchant(id)));
            when(redisService.setReactive(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<MerchantResponse> result = service.findById(id).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data().getId()).isEqualTo(id);
        }

        @Test
        void notFound_throwsException() {
            String id = UNKNOWN_ID;
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findMerchantById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantResponse> result = service.findById(id).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
            assertThat(result.message()).contains("Merchant not found");
        }
    }

    @Nested
    @DisplayName("findByApiKey tests")
    class FindByApiKeyTests {
        @Test
        void cacheMiss_fetchesFromDb() {
            String apiKey = "test-key";
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findByApiKey(anyString()))
                    .thenReturn(Uni.createFrom().item(createMockMerchant(MERCHANT_ID, apiKey)));
            when(redisService.setReactive(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<MerchantResponse> result = service.findByApiKey(apiKey).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data().getApiKey()).isEqualTo(apiKey);
        }

        @Test
        void notFound_throwsException() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findByApiKey(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantResponse> result = service.findByApiKey("bad-key").await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
            assertThat(result.message()).contains("Merchant not found");
        }
    }

    @Nested
    @DisplayName("findByUserId tests")
    class FindByUserIdTests {
        @Test
        void cacheMiss_fetchesFromDb() {
            String userId = USER_ID;
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findByUserId(anyString()))
                    .thenReturn(Uni.createFrom().item(List.of(createMockMerchant(MERCHANT_ID), createMockMerchant(MERCHANT_ID_2))));
            when(redisService.setReactive(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<MerchantResponse>> result = service.findByUserId(userId).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(2);
        }

        @Test
        void returnsEmptyList_whenNoMerchants() {
            String userId = UNKNOWN_ID;
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantQueryRepository.findByUserId(anyString())).thenReturn(Uni.createFrom().item(List.of()));
            when(redisService.setReactive(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<MerchantResponse>> result = service.findByUserId(userId).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).isEmpty();
        }
    }
}