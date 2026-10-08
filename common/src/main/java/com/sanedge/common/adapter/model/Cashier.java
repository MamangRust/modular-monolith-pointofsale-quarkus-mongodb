package com.sanedge.common.adapter.model;

import java.time.Instant;

/**
 * Domain view of a cashier owned by the cashier service.
 */
public record Cashier(
        String id,
        String merchantId,
        String name,
        Instant createdAt,
        Instant updatedAt) {
}
