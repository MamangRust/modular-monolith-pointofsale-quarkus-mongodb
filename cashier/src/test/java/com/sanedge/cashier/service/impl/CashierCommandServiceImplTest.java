package com.sanedge.cashier.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.cashier.domain.requests.CreateCashierRequest;
import com.sanedge.cashier.domain.requests.UpdateCashierRequest;
import com.sanedge.cashier.domain.response.CashierResponse;
import com.sanedge.cashier.domain.response.CashierResponseDeleteAt;
import com.sanedge.cashier.entity.Cashier;
import com.sanedge.cashier.repository.CashierCommandRepository;
import com.sanedge.cashier.repository.CashierQueryRepository;
import com.sanedge.common.adapter.merchant.MerchantQueryPort;
import com.sanedge.common.adapter.user.UserQueryPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CashierCommandServiceImplTest {

    @Mock
    private CashierCommandRepository cashierCommandRepository;

    @Mock
    private CashierQueryRepository cashierQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    @Mock
    private MerchantQueryPort merchantQueryPort;

    @Mock
    private UserQueryPort userQueryPort;

    private CashierCommandServiceImpl cashierCommandService;

    @BeforeEach
    void setUp() {
        cashierCommandService = new CashierCommandServiceImpl(
                cashierCommandRepository,
                cashierQueryRepository,
                redisService,
                tracingMetrics,
                merchantQueryPort,
                userQueryPort);

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
    private static final String MISSING_ID = "507f1f77bcf86cd799439099";

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

    @Test
    void updateCashier_success_updatesName() {
        Cashier existing = createMockCashier(CASHIER_ID, "OldName", MERCHANT_ID, USER_ID);
        UpdateCashierRequest req = new UpdateCashierRequest();
        req.setCashierId("507f1f77bcf86cd799439011");
        req.setName("NewName");

        when(cashierCommandRepository.findById(any(org.bson.types.ObjectId.class))).thenReturn(Uni.createFrom().item(existing));
        lenient().when(cashierCommandRepository.persist(any(Cashier.class))).thenReturn(Uni.createFrom().item(existing));
        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<CashierResponse> response = cashierCommandService.updateCashier(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Cashier updated successfully");
    }

    @Test
    void updateCashier_notFound_throwsException() {
        UpdateCashierRequest req = new UpdateCashierRequest();
        req.setCashierId("507f1f77bcf86cd799439011");
        req.setName("NewName");

        when(cashierCommandRepository.findById(any(org.bson.types.ObjectId.class))).thenReturn(Uni.createFrom().nullItem());

        try {
            cashierCommandService.updateCashier(req).await().indefinitely();
            org.junit.jupiter.api.Assertions.fail("Expected exception");
        } catch (com.sanedge.common.exception.ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("not found");
        }
    }

    @Test
    void trashedCashier_success() {
        Cashier cashier = createMockCashier(CASHIER_ID, "TrashMe", MERCHANT_ID, USER_ID);
        when(cashierCommandRepository.trashed(CASHIER_ID)).thenReturn(Uni.createFrom().item(cashier));
        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<CashierResponseDeleteAt> response = cashierCommandService.trashedCashier("507f1f77bcf86cd799439011").await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Cashier trashed successfully");
        assertThat(response.data()).isNotNull();
    }

    @Test
    void trashedCashier_notFound_throwsException() {
        when(cashierCommandRepository.trashed(MISSING_ID)).thenReturn(Uni.createFrom().nullItem());

        try {
            cashierCommandService.trashedCashier(MISSING_ID).await().indefinitely();
            org.junit.jupiter.api.Assertions.fail("Expected exception");
        } catch (com.sanedge.common.exception.ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("not found");
        }
    }

    @Test
    void restoreCashier_success() {
        Cashier cashier = createMockCashier(CASHIER_ID, "RestoreMe", MERCHANT_ID, USER_ID);
        when(cashierCommandRepository.restore(CASHIER_ID)).thenReturn(Uni.createFrom().item(cashier));
        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<CashierResponseDeleteAt> response = cashierCommandService.restoreCashier("507f1f77bcf86cd799439011").await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Cashier restored successfully");
    }

    @Test
    void restoreCashier_notFound_throwsException() {
        when(cashierCommandRepository.restore(MISSING_ID)).thenReturn(Uni.createFrom().nullItem());

        try {
            cashierCommandService.restoreCashier(MISSING_ID).await().indefinitely();
            org.junit.jupiter.api.Assertions.fail("Expected exception");
        } catch (com.sanedge.common.exception.ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("not found");
        }
    }

    @Test
    void deletePermanent_success() {
        Cashier cashier = createMockCashier(CASHIER_ID, "DeleteMe", MERCHANT_ID, USER_ID);
        when(cashierCommandRepository.deletePermanent(CASHIER_ID)).thenReturn(Uni.createFrom().item(cashier));
        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<Boolean> response = cashierCommandService.deleteCashierPermanent("507f1f77bcf86cd799439011").await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isTrue();
    }

    @Test
    void deletePermanent_notFound_throwsException() {
        when(cashierCommandRepository.deletePermanent(MISSING_ID)).thenReturn(Uni.createFrom().nullItem());

        try {
            cashierCommandService.deleteCashierPermanent(MISSING_ID).await().indefinitely();
            org.junit.jupiter.api.Assertions.fail("Expected exception");
        } catch (com.sanedge.common.exception.ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("not found");
        }
    }

    @Test
    void restoreAllCashier_success() {
        when(cashierCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

        ApiResponse<Boolean> response = cashierCommandService.restoreAllCashier().await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isTrue();
    }

    @Test
    void restoreAllCashier_noTrashed_throwsException() {
        when(cashierCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));

        try {
            cashierCommandService.restoreAllCashier().await().indefinitely();
            org.junit.jupiter.api.Assertions.fail("Expected exception");
        } catch (com.sanedge.common.exception.ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("No trashed cashiers");
        }
    }

    @Test
    void deleteAllCashierPermanent_success() {
        when(cashierCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));

        ApiResponse<Boolean> response = cashierCommandService.deleteAllCashierPermanent().await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isTrue();
    }

    @Test
    void deleteAllCashierPermanent_noTrashed_throwsException() {
        when(cashierCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));

        try {
            cashierCommandService.deleteAllCashierPermanent().await().indefinitely();
            org.junit.jupiter.api.Assertions.fail("Expected exception");
        } catch (com.sanedge.common.exception.ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("No trashed cashiers");
        }
    }
}
