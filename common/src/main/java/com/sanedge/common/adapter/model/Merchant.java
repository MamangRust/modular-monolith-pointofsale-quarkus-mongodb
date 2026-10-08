package com.sanedge.common.adapter.model;

import java.time.Instant;

/**
 * Domain view of a merchant owned by the merchant service.
 */
public record Merchant(
        String id,
        String name,
        String apiKey,
        String status,
        String userId,
        Instant createdAt,
        Instant updatedAt) {
}
