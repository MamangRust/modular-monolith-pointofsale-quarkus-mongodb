package com.sanedge.common.adapter.model;

import java.time.Instant;

/**
 * Domain view of a role owned by the role service.
 */
public record Role(
        String id,
        String name,
        Instant createdAt,
        Instant updatedAt) {
}
