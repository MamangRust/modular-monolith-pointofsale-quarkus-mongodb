package com.sanedge.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.adapter.cashier.CashierQueryPort;
import com.sanedge.common.adapter.merchant.MerchantQueryPort;
import com.sanedge.common.adapter.order_item.OrderItemCommandPort;
import com.sanedge.common.adapter.product.ProductCommandPort;
import com.sanedge.common.adapter.product.ProductQueryPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.CreateOrderRequest;
import com.sanedge.order.domain.requests.UpdateOrderRequest;
import com.sanedge.order.domain.requests.CreateOrderItemRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderCommandRepository;
import com.sanedge.order.repository.OrderQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceImplTest {

    private static final String ORDER_ID = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439012";
    private static final String CASHIER_ID = "507f1f77bcf86cd799439013";
    private static final String MISSING_ID = "507f1f77bcf86cd799439099";

    @Mock
    private OrderQueryRepository orderQueryRepo;
    @Mock
    private OrderCommandRepository orderCommandRepo;
    @Mock
    private Validator validator;
    @Mock
    private RedisService redisService;
    @Mock
    private TracingMetrics tracingMetrics;

    @Mock
    private MerchantQueryPort merchantQueryPort;
    @Mock
    private CashierQueryPort cashierQueryPort;
    @Mock
    private ProductQueryPort productQueryPort;
    @Mock
    private ProductCommandPort productCommandPort;
    @Mock
    private OrderItemCommandPort orderItemCommandPort;

    private OrderCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderCommandServiceImpl(orderQueryRepo, orderCommandRepo, validator, redisService,
                tracingMetrics, merchantQueryPort, cashierQueryPort, productQueryPort, productCommandPort,
                orderItemCommandPort);

        // Lenient stubs for traceAndMeasure
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());
        lenient().when(orderCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));
        lenient().when(orderCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

        // common port stubs for create/update
        lenient().when(merchantQueryPort.findByMerchantId(anyString()))
                .thenReturn(Uni.createFrom().item(new com.sanedge.common.adapter.model.Merchant(
                        MERCHANT_ID, "Merchant", "api-key", "SUCCESS", "507f1f77bcf86cd79943901a",
                        Instant.now(), Instant.now())));
        lenient().when(cashierQueryPort.findById(anyString()))
                .thenReturn(Uni.createFrom().item(new com.sanedge.common.adapter.model.Cashier(
                        CASHIER_ID, MERCHANT_ID, "Cashier", Instant.now(), Instant.now())));
        lenient().when(productQueryPort.findById(anyString()))
                .thenReturn(Uni.createFrom().item(new com.sanedge.common.adapter.model.Product(
                        "1", MERCHANT_ID, "507f1f77bcf86cd799439014", "Product", "Desc", 5000, 100,
                        "Brand", 1, 0.0f, "slug", "img.png", "barcode", Instant.now(), Instant.now())));
        lenient().when(productCommandPort.update(any()))
                .thenAnswer(inv -> Uni.createFrom().item(new com.sanedge.common.adapter.model.Product(
                        "1", MERCHANT_ID, "507f1f77bcf86cd799439014", "Product", "Desc", 5000, 100,
                        "Brand", 1, 0.0f, "slug", "img.png", "barcode", Instant.now(), Instant.now())));
        lenient().when(orderItemCommandPort.createOrderItem(any()))
                .thenAnswer(inv -> Uni.createFrom().item(new com.sanedge.common.adapter.model.OrderItem(
                        "507f1f77bcf86cd799439015", ORDER_ID, "1", 2, 5000, Instant.now(), Instant.now())));
        lenient().when(orderItemCommandPort.updateOrderItem(any()))
                .thenAnswer(inv -> Uni.createFrom().item(new com.sanedge.common.adapter.model.OrderItem(
                        "507f1f77bcf86cd799439015", ORDER_ID, "1", 2, 5000, Instant.now(), Instant.now())));
    }

    private Order createMockOrder(String id) {
        Order order = new Order();
        order.setOrderId(id);
        order.setCashierId(new ObjectId(CASHIER_ID));
        order.setMerchantId(new ObjectId(MERCHANT_ID));
        order.setTotalPrice(50000L);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }

    // ---------- create ----------
    @Nested
    @DisplayName("create tests")
    class CreateTests {
        @Test
        void success() {
            CreateOrderRequest req = new CreateOrderRequest();
            req.setMerchantId(MERCHANT_ID);
            req.setCashierId(CASHIER_ID);
            CreateOrderItemRequest item = new CreateOrderItemRequest();
            item.setProductId("1");
            item.setQuantity(2);
            item.setPrice(5000);
            req.setItems(List.of(item));

            when(orderCommandRepo.persist(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setOrderId(ORDER_ID);
                return Uni.createFrom().item(o);
            });

            ApiResponse<OrderResponse> resp = service.create(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getId()).isEqualTo(ORDER_ID);
            assertThat(resp.data().getTotalPrice()).isEqualTo(10000L); // 2 * 5000
        }

        @Test
        void merchantNotFound_returnsError() {
            when(merchantQueryPort.findByMerchantId(MERCHANT_ID))
                    .thenReturn(Uni.createFrom().failure(
                            new ResourceNotFoundException("Merchant not found: " + MERCHANT_ID)));

            CreateOrderRequest req = new CreateOrderRequest();
            req.setMerchantId(MERCHANT_ID);
            req.setCashierId(CASHIER_ID);
            ApiResponse<OrderResponse> resp = service.create(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }
    }

    // ---------- trash ----------
    @Nested
    @DisplayName("trash tests")
    class TrashTests {
        @Test
        void success() {
            String id = ORDER_ID;
            Order trashed = createMockOrder(ORDER_ID);
            trashed.setDeletedAt(Instant.now());
            when(orderCommandRepo.trashed(anyString())).thenReturn(Uni.createFrom().item(trashed));

            ApiResponse<OrderResponseDeleteAt> resp = service.trash(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNotNull();
        }

        @Test
        void notFound_returnsError() {
            String id = MISSING_ID;
            when(orderCommandRepo.trashed(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<OrderResponseDeleteAt> resp = service.trash(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order not found");
        }
    }

    // ---------- restore ----------
    @Nested
    @DisplayName("restore tests")
    class RestoreTests {
        @Test
        void success() {
            String id = ORDER_ID;
            when(orderCommandRepo.restore(anyString())).thenReturn(Uni.createFrom().item(createMockOrder(ORDER_ID)));

            ApiResponse<OrderResponseDeleteAt> resp = service.restore(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNull();
        }

        @Test
        void notFound_returnsError() {
            String id = MISSING_ID;
            when(orderCommandRepo.restore(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<OrderResponseDeleteAt> resp = service.restore(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order not found");
        }
    }

    // ---------- delete (permanent) ----------
    @Nested
    @DisplayName("delete tests")
    class DeleteTests {
        @Test
        void success() {
            String id = ORDER_ID;
            when(orderCommandRepo.deletePermanent(anyString()))
                    .thenReturn(Uni.createFrom().item(createMockOrder(ORDER_ID)));

            ApiResponse<Boolean> resp = service.delete(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void notFound_returnsError() {
            String id = MISSING_ID;
            when(orderCommandRepo.deletePermanent(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<Boolean> resp = service.delete(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order not found");
        }
    }

    // ---------- restoreAll ----------
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
            when(orderCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.restoreAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No orders found in trash");
        }
    }

    // ---------- deleteAll ----------
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
            when(orderCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.deleteAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No orders found in trash");
        }
    }

    // ---------- updateOrderTotalPrice ----------
    @Nested
    @DisplayName("updateOrderTotalPrice tests")
    class UpdateOrderTotalPriceTests {
        @Test
        void success() {
            String orderId = ORDER_ID;
            Integer newTotal = 99999;
            Order order = createMockOrder(ORDER_ID);
            order.setTotalPrice(newTotal.longValue());
            when(orderQueryRepo.findOrderById(anyString())).thenReturn(Uni.createFrom().item(createMockOrder(ORDER_ID)));
            when(orderCommandRepo.persist(any(Order.class))).thenReturn(Uni.createFrom().item(order));

            ApiResponse<OrderResponse> resp = service.updateOrderTotalPrice(orderId, newTotal).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getTotalPrice()).isEqualTo(newTotal.longValue());
        }

        @Test
        void orderNotFound_returnsError() {
            when(orderQueryRepo.findOrderById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<OrderResponse> resp = service.updateOrderTotalPrice(MISSING_ID, 5000).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order not found");
        }
    }
}
