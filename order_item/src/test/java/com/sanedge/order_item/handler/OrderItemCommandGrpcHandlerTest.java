package com.sanedge.order_item.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.service.OrderItemCommandService;

import io.smallrye.mutiny.Uni;
import pb.order_item.OrderItem;
import pb.order_item.OrderItemCommand;

@ExtendWith(MockitoExtension.class)
class OrderItemCommandGrpcHandlerTest {

    private static final String ITEM_ID = "507f1f77bcf86cd799439011";
    private static final String ORDER_ID = "507f1f77bcf86cd7994390a1";
    private static final String PRODUCT_ID = "507f1f77bcf86cd7994390b2";

    @Mock
    private OrderItemCommandService orderItemCommandService;

    private OrderItemCommandGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderItemCommandGrpcHandler();
        handler.orderItemCommandService = orderItemCommandService;
    }

    private com.sanedge.order_item.entity.OrderItem createMockOrderItem(String id) {
        com.sanedge.order_item.entity.OrderItem item = new com.sanedge.order_item.entity.OrderItem();
        item.setOrderItemId(id);
        item.setOrderId(new org.bson.types.ObjectId(ORDER_ID));
        item.setProductId(new org.bson.types.ObjectId(PRODUCT_ID));
        item.setQuantity(2);
        item.setPrice(5000);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    // createOrderItem
    @Test
    @DisplayName("createOrderItem - success")
    void createOrderItem_Success() {
        OrderItemCommand.CreateOrderItemRequest request = OrderItemCommand.CreateOrderItemRequest.newBuilder()
                .setOrderId(ORDER_ID)
                .setProductId(PRODUCT_ID)
                .setQuantity(2)
                .setPrice(5000)
                .build();

        OrderItemResponse data = OrderItemResponse.from(createMockOrderItem(ITEM_ID));
        ApiResponse<OrderItemResponse> apiResp = ApiResponse.success("Order item created", data);
        when(orderItemCommandService.create(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItem response = handler.createOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getId()).isEqualTo(ITEM_ID);
        assertThat(response.getData().getOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("createOrderItem - error response")
    void createOrderItem_Error() {
        OrderItemCommand.CreateOrderItemRequest request = OrderItemCommand.CreateOrderItemRequest.newBuilder().build();
        ApiResponse<OrderItemResponse> apiResp = new ApiResponse<>("error", "Order not found", null);
        when(orderItemCommandService.create(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItem response = handler.createOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getMessage()).isEqualTo("Order not found");
    }

    @Test
    @DisplayName("createOrderItem - failure propagates")
    void createOrderItem_Failure() {
        OrderItemCommand.CreateOrderItemRequest request = OrderItemCommand.CreateOrderItemRequest.newBuilder().build();
        when(orderItemCommandService.create(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

        OrderItem.ApiResponseOrderItem response = handler.createOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
    }

    // updateOrderItem
    @Test
    @DisplayName("updateOrderItem - success")
    void updateOrderItem_Success() {
        OrderItemCommand.UpdateOrderItemRequest request = OrderItemCommand.UpdateOrderItemRequest.newBuilder()
                .setOrderItemId(ITEM_ID)
                .setOrderId(ORDER_ID)
                .setProductId(PRODUCT_ID)
                .setQuantity(3)
                .setPrice(6000)
                .build();

        OrderItemResponse data = OrderItemResponse.from(createMockOrderItem(ITEM_ID));
        ApiResponse<OrderItemResponse> apiResp = ApiResponse.success("Order item updated", data);
        when(orderItemCommandService.update(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItem response = handler.updateOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateOrderItem - error response")
    void updateOrderItem_Error() {
        OrderItemCommand.UpdateOrderItemRequest request = OrderItemCommand.UpdateOrderItemRequest.newBuilder().build();
        when(orderItemCommandService.update(any()))
                .thenReturn(Uni.createFrom().item(new ApiResponse<>("error", "not found", null)));

        OrderItem.ApiResponseOrderItem response = handler.updateOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
    }

    // trashedOrderItem
    @Test
    @DisplayName("trashedOrderItem - success")
    void trashed_Success() {
        OrderItem.FindByIdOrderItemRequest request = OrderItem.FindByIdOrderItemRequest.newBuilder()
                .setOrderItemId(ITEM_ID).build();
        OrderItemResponseDeleteAt data = OrderItemResponseDeleteAt.from(createMockOrderItem(ITEM_ID));
        data.setDeletedAt(Instant.now().toString());
        ApiResponse<OrderItemResponseDeleteAt> apiResp = ApiResponse.success("Trashed", data);
        when(orderItemCommandService.trash(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItemDeleteAt response = handler.trashedOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().hasDeletedAt()).isTrue();
    }

    // restoreOrderItem
    @Test
    @DisplayName("restoreOrderItem - success")
    void restore_Success() {
        OrderItem.FindByIdOrderItemRequest request = OrderItem.FindByIdOrderItemRequest.newBuilder()
                .setOrderItemId(ITEM_ID).build();
        OrderItemResponseDeleteAt data = OrderItemResponseDeleteAt.from(createMockOrderItem(ITEM_ID));
        ApiResponse<OrderItemResponseDeleteAt> apiResp = ApiResponse.success("Restored", data);
        when(orderItemCommandService.restore(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItemDeleteAt response = handler.restoreOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().hasDeletedAt()).isFalse();
    }

    // deleteOrderItemPermanent
    @Test
    @DisplayName("deleteOrderItemPermanent - success")
    void deletePermanent_Success() {
        OrderItem.FindByIdOrderItemRequest request = OrderItem.FindByIdOrderItemRequest.newBuilder()
                .setOrderItemId(ITEM_ID).build();
        ApiResponse<Boolean> apiResp = ApiResponse.success("Deleted permanently", true);
        when(orderItemCommandService.delete(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItemDelete response = handler.deleteOrderItemPermanent(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Deleted permanently");
    }

    // restoreAllOrderItem
    @Test
    @DisplayName("restoreAllOrderItem - success")
    void restoreAll_Success() {
        ApiResponse<Boolean> apiResp = ApiResponse.success("All restored", true);
        when(orderItemCommandService.restoreAll()).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItemAll response = handler.restoreAllOrderItem(com.google.protobuf.Empty.getDefaultInstance())
                .await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("All restored");
    }

    // deleteAllOrderItemPermanent
    @Test
    @DisplayName("deleteAllOrderItemPermanent - success")
    void deleteAll_Success() {
        ApiResponse<Boolean> apiResp = ApiResponse.success("All permanently deleted", true);
        when(orderItemCommandService.deleteAll()).thenReturn(Uni.createFrom().item(apiResp));

        OrderItem.ApiResponseOrderItemAll response = handler.deleteAllOrderItemPermanent(com.google.protobuf.Empty.getDefaultInstance())
                .await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("All permanently deleted");
    }

    // failure propagations
    @Test
    @DisplayName("trashedOrderItem - failure propagates")
    void trashed_Failure() {
        OrderItem.FindByIdOrderItemRequest request = OrderItem.FindByIdOrderItemRequest.newBuilder().build();
        when(orderItemCommandService.trash(anyString())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

        OrderItem.ApiResponseOrderItemDeleteAt response = handler.trashedOrderItem(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
    }

    @Test
    @DisplayName("restoreAllOrderItem - failure propagates")
    void restoreAll_Failure() {
        when(orderItemCommandService.restoreAll()).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

        OrderItem.ApiResponseOrderItemAll response = handler.restoreAllOrderItem(com.google.protobuf.Empty.getDefaultInstance())
                .await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
    }
}
