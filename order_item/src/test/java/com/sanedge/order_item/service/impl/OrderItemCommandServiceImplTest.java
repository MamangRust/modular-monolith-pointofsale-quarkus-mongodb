package com.sanedge.order_item.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.repository.OrderItemRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class OrderItemCommandServiceImplTest {

    private static final String ITEM_ID = "507f1f77bcf86cd799439011";
    private static final String ORDER_ID = "507f1f77bcf86cd7994390a1";
    private static final String PRODUCT_ID = "507f1f77bcf86cd7994390b2";

    @Mock private OrderItemRepository orderItemRepository;
    @Mock private Validator validator;
    @Mock private RedisService redisService;
    @Mock private TracingMetrics tracingMetrics;

    @Mock private com.sanedge.common.adapter.order.OrderQueryPort orderQueryPort;
    @Mock private com.sanedge.common.adapter.order.OrderCommandPort orderCommandPort;
    @Mock private com.sanedge.common.adapter.product.ProductQueryPort productQueryPort;
    @Mock private com.sanedge.common.adapter.product.ProductCommandPort productCommandPort;

    private OrderItemCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderItemCommandServiceImpl(orderItemRepository, validator, redisService, tracingMetrics,
                orderQueryPort, orderCommandPort, productQueryPort, productCommandPort);

        // Lenient stub for traceAndMeasure
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());
        lenient().when(orderItemRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));
        lenient().when(orderItemRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

        // common port stubs
        lenient().when(orderQueryPort.findById(anyString()))
                .thenReturn(Uni.createFrom().item(new com.sanedge.common.adapter.model.Order(
                        ORDER_ID, "507f1f77bcf86cd7994390c3", "507f1f77bcf86cd7994390d4", 10000,
                        Instant.now(), Instant.now())));
        lenient().when(orderCommandPort.updateOrderTotalPrice(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Uni.createFrom().voidItem());
        lenient().when(productQueryPort.findById(anyString()))
                .thenReturn(Uni.createFrom().item(new com.sanedge.common.adapter.model.Product(
                        PRODUCT_ID, "507f1f77bcf86cd7994390c3", "507f1f77bcf86cd7994390e5", "Product", "Desc",
                        5000, 100, "Brand", 1, 0.0f, "slug", "img.png", "barcode", Instant.now(), Instant.now())));
        lenient().when(productCommandPort.update(any()))
                .thenAnswer(inv -> Uni.createFrom().item(new com.sanedge.common.adapter.model.Product(
                        PRODUCT_ID, "507f1f77bcf86cd7994390c3", "507f1f77bcf86cd7994390e5", "Product", "Desc",
                        5000, 100, "Brand", 1, 0.0f, "slug", "img.png", "barcode", Instant.now(), Instant.now())));
    }

    private OrderItem createMockOrderItem(String id) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(id);
        item.setOrderId(new org.bson.types.ObjectId(ORDER_ID));
        item.setProductId(new org.bson.types.ObjectId(PRODUCT_ID));
        item.setQuantity(2);
        item.setPrice(5000);
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    // ---------- create ----------
    @Nested
    @DisplayName("create tests")
    class CreateTests {
        @Test void success() {
            CreateOrderItemRequest req = new CreateOrderItemRequest();
            req.setOrderId(ORDER_ID);
            req.setProductId(PRODUCT_ID);
            req.setQuantity(2);
            req.setPrice(5000);

            when(orderItemRepository.persist(any(OrderItem.class))).thenAnswer(inv -> {
                OrderItem item = inv.getArgument(0);
                item.setOrderItemId(ITEM_ID);
                return Uni.createFrom().item(item);
            });
            when(orderItemRepository.findOrderItemByOrder(anyString()))
                    .thenReturn(Uni.createFrom().item(List.of(createMockOrderItem(ITEM_ID))));

            ApiResponse<OrderItemResponse> resp = service.create(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getId()).isEqualTo(ITEM_ID);
        }

        @Test void orderNotFound_throwsException() {
            when(orderQueryPort.findById(ORDER_ID))
                    .thenReturn(Uni.createFrom().failure(
                            new ResourceNotFoundException("Order not found: " + ORDER_ID)));

            CreateOrderItemRequest req = new CreateOrderItemRequest();
            req.setOrderId(ORDER_ID);
            req.setProductId(PRODUCT_ID);
            req.setQuantity(2);
            req.setPrice(5000);
            ApiResponse<OrderItemResponse> resp = service.create(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order not found");
        }
    }

    // ---------- update ----------
    @Nested
    @DisplayName("update tests")
    class UpdateTests {
        @Test void success() {
            UpdateOrderItemRequest req = new UpdateOrderItemRequest();
            req.setOrderItemId(ITEM_ID);
            req.setOrderId(ORDER_ID);
            req.setProductId(PRODUCT_ID);
            req.setQuantity(3);
            req.setPrice(5000);

            OrderItem savedItem = createMockOrderItem(ITEM_ID);
            savedItem.setQuantity(3);
            savedItem.setPrice(5000);
            when(orderItemRepository.findById(any())).thenReturn(Uni.createFrom().item(createMockOrderItem(ITEM_ID)));
            when(orderItemRepository.persist(any(OrderItem.class))).thenReturn(Uni.createFrom().item(savedItem));
            when(orderItemRepository.findOrderItemByOrder(anyString()))
                    .thenReturn(Uni.createFrom().item(List.of(createMockOrderItem(ITEM_ID))));

            ApiResponse<OrderItemResponse> resp = service.update(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }

        @Test void notFound_returnsError() {
            UpdateOrderItemRequest req = new UpdateOrderItemRequest();
            req.setOrderItemId("507f1f77bcf86cd7994390ff");
            when(orderItemRepository.findById(any())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<OrderItemResponse> resp = service.update(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order item not found");
        }
    }

    // ---------- trash ----------
    @Nested
    @DisplayName("trash tests")
    class TrashTests {
        @Test void success() {
            String id = ITEM_ID;
            OrderItem trashed = createMockOrderItem(ITEM_ID);
            trashed.setDeletedAt(Instant.now());
            when(orderItemRepository.trashed(anyString())).thenReturn(Uni.createFrom().item(trashed));
            when(orderItemRepository.findOrderItemByOrder(anyString()))
                    .thenReturn(Uni.createFrom().item(List.of(createMockOrderItem(ITEM_ID))));

            ApiResponse<OrderItemResponseDeleteAt> resp = service.trash(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNotNull();
        }

        @Test void notFound_returnsError() {
            when(orderItemRepository.trashed(anyString())).thenReturn(Uni.createFrom().nullItem());
            ApiResponse<OrderItemResponseDeleteAt> resp = service.trash(ITEM_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order item not found");
        }
    }

    // ---------- restore ----------
    @Nested
    @DisplayName("restore tests")
    class RestoreTests {
        @Test void success() {
            String id = ITEM_ID;
            when(orderItemRepository.restore(anyString())).thenReturn(Uni.createFrom().item(createMockOrderItem(ITEM_ID)));
            when(orderItemRepository.findOrderItemByOrder(anyString()))
                    .thenReturn(Uni.createFrom().item(List.of(createMockOrderItem(ITEM_ID))));

            ApiResponse<OrderItemResponseDeleteAt> resp = service.restore(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNull();
        }

        @Test void notFound_returnsError() {
            when(orderItemRepository.restore(anyString())).thenReturn(Uni.createFrom().nullItem());
            ApiResponse<OrderItemResponseDeleteAt> resp = service.restore(ITEM_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order item not found");
        }
    }

    // ---------- delete ----------
    @Nested
    @DisplayName("delete tests")
    class DeleteTests {
        @Test void success() {
            String id = ITEM_ID;
            when(orderItemRepository.deletePermanent(anyString())).thenReturn(Uni.createFrom().item(createMockOrderItem(ITEM_ID)));
            when(orderItemRepository.findOrderItemByOrder(anyString()))
                    .thenReturn(Uni.createFrom().item(List.of(createMockOrderItem(ITEM_ID))));

            ApiResponse<Boolean> resp = service.delete(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test void notFound_returnsError() {
            when(orderItemRepository.deletePermanent(anyString())).thenReturn(Uni.createFrom().nullItem());
            ApiResponse<Boolean> resp = service.delete(ITEM_ID).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Order item not found");
        }
    }

    // ---------- restoreAll ----------
    @Nested
    @DisplayName("restoreAll tests")
    class RestoreAllTests {
        @Test void success() {
            ApiResponse<Boolean> resp = service.restoreAll().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test void noTrashed_throwsException() {
            when(orderItemRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.restoreAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No order items found in trash");
        }
    }

    // ---------- deleteAll ----------
    @Nested
    @DisplayName("deleteAll tests")
    class DeleteAllTests {
        @Test void success() {
            ApiResponse<Boolean> resp = service.deleteAll().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test void noTrashed_throwsException() {
            when(orderItemRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.deleteAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No order items found in trash");
        }
    }
}
