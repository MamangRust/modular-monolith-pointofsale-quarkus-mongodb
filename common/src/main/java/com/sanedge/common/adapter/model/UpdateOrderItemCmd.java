package com.sanedge.common.adapter.model;

/**
 * Command to update an order item in the order_item service.
 */
public record UpdateOrderItemCmd(
        String orderItemId,
        String orderId,
        String productId,
        int quantity,
        int price) {
}
