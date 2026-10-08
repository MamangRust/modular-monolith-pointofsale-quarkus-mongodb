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
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.service.OrderItemQueryService;

import io.smallrye.mutiny.Uni;
import pb.order_item.OrderItemQuery;

@ExtendWith(MockitoExtension.class)
class OrderItemQueryGrpcHandlerTest {

    private static final String ITEM_ID = "507f1f77bcf86cd799439011";
    private static final String ORDER_ID = "507f1f77bcf86cd7994390a1";
    private static final String PRODUCT_ID = "507f1f77bcf86cd7994390b2";

    @Mock
    private OrderItemQueryService orderItemQueryService;

    private OrderItemQueryGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderItemQueryGrpcHandler();
        handler.orderItemQueryService = orderItemQueryService;
    }

    private OrderItem createMockOrderItem(String id) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(id);
        item.setOrderId(new org.bson.types.ObjectId(ORDER_ID));
        item.setProductId(new org.bson.types.ObjectId(PRODUCT_ID));
        item.setQuantity(2);
        item.setPrice(5000);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    private OrderItemResponse createOrderItemResponse(String id) {
        return OrderItemResponse.from(createMockOrderItem(id));
    }

    private OrderItemResponseDeleteAt createOrderItemDeleteAt(String id) {
        OrderItemResponseDeleteAt r = OrderItemResponseDeleteAt.from(createMockOrderItem(id));
        r.setDeletedAt(Instant.now().toString());
        return r;
    }

    // findAll
    @Test
    @DisplayName("findAll - success")
    void findAll_Success() {
        pb.order_item.OrderItem.FindAllOrderItemRequest request = pb.order_item.OrderItem.FindAllOrderItemRequest.newBuilder()
                .setSearch("").setPage(1).setPageSize(10).build();

        OrderItemResponse data = createOrderItemResponse(ITEM_ID);
        ApiResponsePagination<List<OrderItemResponse>> apiResp = new ApiResponsePagination<>("success",
                "Order items retrieved", List.of(data), new PaginationMeta(1, 10, 1, 1));
        when(orderItemQueryService.findAll(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItemQuery.ApiResponsePaginationOrderItem response = handler.findAll(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getData(0).getProductId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    @DisplayName("findAll - error response")
    void findAll_Error() {
        pb.order_item.OrderItem.FindAllOrderItemRequest request = pb.order_item.OrderItem.FindAllOrderItemRequest.newBuilder().build();
        ApiResponsePagination<List<OrderItemResponse>> apiResp = new ApiResponsePagination<>("error", "DB error", null, null);
        when(orderItemQueryService.findAll(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItemQuery.ApiResponsePaginationOrderItem response = handler.findAll(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
    }

    // findByActive
    @Test
    @DisplayName("findByActive - success")
    void findByActive_Success() {
        pb.order_item.OrderItem.FindAllOrderItemRequest request = pb.order_item.OrderItem.FindAllOrderItemRequest.newBuilder()
                .setPage(1).setPageSize(10).build();

        OrderItemResponseDeleteAt data = createOrderItemDeleteAt(ITEM_ID);
        ApiResponsePagination<List<OrderItemResponseDeleteAt>> apiResp = new ApiResponsePagination<>("success",
                "Active items", List.of(data), new PaginationMeta(1, 10, 1, 1));
        when(orderItemQueryService.findByActive(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItemQuery.ApiResponsePaginationOrderItemDeleteAt response = handler.findByActive(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).hasDeletedAt()).isTrue();
    }

    @Test
    @DisplayName("findByActive - failure propagates")
    void findByActive_Error() {
        pb.order_item.OrderItem.FindAllOrderItemRequest request = pb.order_item.OrderItem.FindAllOrderItemRequest.newBuilder().build();
        when(orderItemQueryService.findByActive(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
        OrderItemQuery.ApiResponsePaginationOrderItemDeleteAt response = handler.findByActive(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
    }

    // findByTrashed
    @Test
    @DisplayName("findByTrashed - success")
    void findByTrashed_Success() {
        pb.order_item.OrderItem.FindAllOrderItemRequest request = pb.order_item.OrderItem.FindAllOrderItemRequest.newBuilder().build();
        OrderItemResponseDeleteAt data = createOrderItemDeleteAt(ITEM_ID);
        ApiResponsePagination<List<OrderItemResponseDeleteAt>> apiResp = new ApiResponsePagination<>("success",
                "Trashed items", List.of(data), new PaginationMeta(1, 10, 1, 1));
        when(orderItemQueryService.findByTrashed(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItemQuery.ApiResponsePaginationOrderItemDeleteAt response = handler.findByTrashed(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).hasDeletedAt()).isTrue();
    }

    // findOrderItemByOrder
    @Test
    @DisplayName("findOrderItemByOrder - success")
    void findOrderItemByOrder_Success() {
        pb.order_item.OrderItem.FindByIdOrderItemRequest request = pb.order_item.OrderItem.FindByIdOrderItemRequest.newBuilder()
                .setOrderItemId(ITEM_ID).build();

        OrderItemResponse data = createOrderItemResponse(ITEM_ID);
        ApiResponse<List<OrderItemResponse>> apiResp = ApiResponse.success("Items by order", List.of(data));
        when(orderItemQueryService.findOrderItemByOrder(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        pb.order_item.OrderItem.ApiResponsesOrderItem response = handler.findOrderItemByOrder(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).getId()).isEqualTo(ITEM_ID);
    }

    @Test
    @DisplayName("findOrderItemByOrder - error")
    void findOrderItemByOrder_Error() {
        pb.order_item.OrderItem.FindByIdOrderItemRequest request = pb.order_item.OrderItem.FindByIdOrderItemRequest.newBuilder().build();
        when(orderItemQueryService.findOrderItemByOrder(anyString())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
        pb.order_item.OrderItem.ApiResponsesOrderItem response = handler.findOrderItemByOrder(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("failed");
    }

    // edge cases
    @Test
    @DisplayName("findAll - empty list")
    void findAll_Empty() {
        pb.order_item.OrderItem.FindAllOrderItemRequest request = pb.order_item.OrderItem.FindAllOrderItemRequest.newBuilder().build();
        ApiResponsePagination<List<OrderItemResponse>> apiResp = new ApiResponsePagination<>("success",
                "No items", List.of(), new PaginationMeta(1, 10, 0, 0));
        when(orderItemQueryService.findAll(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderItemQuery.ApiResponsePaginationOrderItem response = handler.findAll(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isZero();
    }
}
