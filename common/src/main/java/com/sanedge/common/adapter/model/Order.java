package com.sanedge.common.adapter.model;

import java.time.Instant;

/**
 * Domain view of an order owned by the order service.
 */
public record Order(
        String id,
        String merchantId,
        String cashierId,
        int totalPrice,
        Instant createdAt,
        Instant updatedAt) {
}
