package com.sanedge.merchant.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.bson.types.ObjectId;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.user.UserQueryPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.enums.Status;
import com.sanedge.common.exception.ResourceAlreadyExistsException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant.domain.requests.CreateMerchantRequest;
import com.sanedge.merchant.domain.requests.UpdateMerchantRequest;
import com.sanedge.merchant.domain.response.MerchantResponse;
import com.sanedge.merchant.domain.response.MerchantResponseDeleteAt;
import com.sanedge.merchant.entity.Merchant;
import com.sanedge.merchant.repository.MerchantCommandRepository;
import com.sanedge.merchant.repository.MerchantQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class MerchantCommandServiceImplTest {

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private MerchantQueryRepository merchantQueryRepo;

    @Mock
    private MerchantCommandRepository merchantCommandRepo;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private MerchantCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantCommandServiceImpl(
                merchantQueryRepo,
                merchantCommandRepo,
                redisService,
                tracingMetrics,
                userQueryPort);

        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());
        lenient().when(merchantCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));
        lenient().when(merchantCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));
    }

    private static final String MERCHANT_ID = "507f1f77bcf86cd799439011";
    private static final String UNKNOWN_ID = "ffffffffffffffffffffffff";
    private static final String USER_10 = "507f1f77bcf86cd79943901a";
    private static final String USER_99 = "507f1f77bcf86cd799439063";
    private static final String USER_100 = "507f1f77bcf86cd799439064";
    private static final String USER_200 = "507f1f77bcf86cd7994390c8";

    private Merchant createMerchant(String id, String name, String apiKey, Status status) {
        Merchant m = new Merchant();
        m.id = new ObjectId(id);
        m.setMerchantNo(UUID.randomUUID());
        m.setName(name);
        m.setApiKey(apiKey);
        m.setUserId(new ObjectId(USER_100));
        m.setStatus(status);
        m.setCreatedAt(Instant.now());
        m.setUpdatedAt(Instant.now());
        return m;
    }

    private Merchant createMerchant(String id, String name, String apiKey) {
        return createMerchant(id, name, apiKey, Status.PENDING);
    }

    private CreateMerchantRequest createReq(String name, String userId) {
        CreateMerchantRequest r = new CreateMerchantRequest();
        r.setName(name);
        r.setUserId(userId);
        return r;
    }

    private User adapterUser(String id) {
        return new User(id, "First", "Last", "user-" + id + "@example.com", Instant.now(), Instant.now());
    }

    private UpdateMerchantRequest updateReq(String id, String name, String userId, String status) {
        UpdateMerchantRequest r = new UpdateMerchantRequest();
        r.setMerchantId(id);
        r.setName(name);
        r.setUserId(userId);
        r.setStatus(status);
        return r;
    }

    @Nested
    @DisplayName("createMerchant tests")
    class CreateMerchantTests {
        @Test
        void success() {
            CreateMerchantRequest req = createReq("New Merchant", USER_10);
            when(userQueryPort.findById(USER_10)).thenReturn(Uni.createFrom().item(adapterUser(USER_10)));
            when(merchantQueryRepo.existsByName("New Merchant")).thenReturn(Uni.createFrom().item(false));

            Merchant saved = createMerchant(MERCHANT_ID, "New Merchant", "test-api-key");
            when(merchantCommandRepo.persist(any(Merchant.class))).thenReturn(Uni.createFrom().item(saved));

            ApiResponse<MerchantResponse> resp = service.createMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getName()).isEqualTo("New Merchant");
            assertThat(resp.data().getApiKey()).isNotNull();
        }

        @Test
        void userNotFound_throwsException() {
            CreateMerchantRequest req = createReq("Merchant", USER_99);
            when(userQueryPort.findById(USER_99))
                    .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("User not found: " + USER_99)));

            // The exception is caught and converted to an error response
            ApiResponse<MerchantResponse> resp = service.createMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("User not found");
        }

        @Test
        void nameAlreadyExists_throwsException() {
            CreateMerchantRequest req = createReq("Existing", USER_10);
            when(userQueryPort.findById(USER_10)).thenReturn(Uni.createFrom().item(adapterUser(USER_10)));
            when(merchantQueryRepo.existsByName("Existing")).thenReturn(Uni.createFrom().item(true));

            ApiResponse<MerchantResponse> resp = service.createMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("name already taken");
        }
    }

    @Nested
    @DisplayName("updateMerchant tests")
    class UpdateMerchantTests {
        @Test
        void success() {
            UpdateMerchantRequest req = updateReq(MERCHANT_ID, "Updated", USER_100, "SUCCESS");
            Merchant existing = createMerchant(MERCHANT_ID, "Old", "old-key");
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().item(existing));

            when(userQueryPort.findById(USER_100)).thenReturn(Uni.createFrom().item(adapterUser(USER_100)));

            Merchant saved = createMerchant(MERCHANT_ID, "Updated", "old-key", Status.SUCCESS);
            when(merchantCommandRepo.persist(any(Merchant.class))).thenReturn(Uni.createFrom().item(saved));

            ApiResponse<MerchantResponse> resp = service.updateMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getName()).isEqualTo("Updated");
        }

        @Test
        void merchantNotFound_throwsException() {
            UpdateMerchantRequest req = updateReq(UNKNOWN_ID, "X", null, "SUCCESS");
            when(merchantQueryRepo.findMerchantById(UNKNOWN_ID)).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantResponse> resp = service.updateMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }

        @Test
        void userNotFoundInUpdate_returnsError() {
            UpdateMerchantRequest req = updateReq(MERCHANT_ID, "Y", USER_200, "SUCCESS");
            Merchant existing = createMerchant(MERCHANT_ID, "Old", "key");
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().item(existing));
            when(userQueryPort.findById(USER_200))
                    .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("User not found: " + USER_200)));

            ApiResponse<MerchantResponse> resp = service.updateMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("User not found");
        }
    }

    @Nested
    @DisplayName("trashMerchant tests")
    class TrashMerchantTests {
        @Test
        void success() {
            Merchant merchant = createMerchant(MERCHANT_ID, "Trash", "api");
            merchant.setDeletedAt(Instant.now());
            when(merchantCommandRepo.trashed(MERCHANT_ID)).thenReturn(Uni.createFrom().item(merchant));

            ApiResponse<MerchantResponseDeleteAt> resp = service.trashMerchant(MERCHANT_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNotNull();
        }

        @Test
        void notFound_returnsError() {
            when(merchantCommandRepo.trashed(MERCHANT_ID)).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<MerchantResponseDeleteAt> resp = service.trashMerchant(MERCHANT_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }
    }

    @Nested
    @DisplayName("restoreMerchant tests")
    class RestoreMerchantTests {
        @Test
        void success() {
            when(merchantCommandRepo.restore(MERCHANT_ID)).thenReturn(Uni.createFrom().item(createMerchant(MERCHANT_ID, "Restore", "api")));
            ApiResponse<MerchantResponseDeleteAt> resp = service.restoreMerchant(MERCHANT_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }

        @Test
        void notFound_returnsError() {
            when(merchantCommandRepo.restore(MERCHANT_ID)).thenReturn(Uni.createFrom().nullItem());
            ApiResponse<MerchantResponseDeleteAt> resp = service.restoreMerchant(MERCHANT_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }
    }

    @Nested
    @DisplayName("deleteMerchant tests")
    class DeleteMerchantTests {
        @Test
        void success() {
            Merchant existing = createMerchant(MERCHANT_ID, "Del", "dk");
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().item(existing));
            when(merchantCommandRepo.deletePermanent(MERCHANT_ID)).thenReturn(Uni.createFrom().item(true));

            ApiResponse<Boolean> resp = service.deleteMerchant(MERCHANT_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void notFound_returnsError() {
            when(merchantQueryRepo.findMerchantById(MERCHANT_ID)).thenReturn(Uni.createFrom().nullItem());
            ApiResponse<Boolean> resp = service.deleteMerchant(MERCHANT_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }
    }

    @Nested
    @DisplayName("restoreAll tests")
    class RestoreAllTests {
        @Test
        void success() {
            ApiResponse<Boolean> resp = service.restoreAll().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void noTrashed_throwsException() {
            when(merchantCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.restoreAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed merchants");
        }
    }

    @Nested
    @DisplayName("deleteAll tests")
    class DeleteAllTests {
        @Test
        void success() {
            ApiResponse<Boolean> resp = service.deleteAll().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void noTrashed_throwsException() {
            when(merchantCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.deleteAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed merchants");
        }
    }
}