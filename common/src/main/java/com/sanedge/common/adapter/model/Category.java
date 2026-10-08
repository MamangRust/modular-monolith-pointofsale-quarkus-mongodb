package com.sanedge.common.adapter.model;

import java.time.Instant;

public record Category(
        String id,
        String name,
        String description,
        String slugCategory,
        String imageCategory,
        Instant createdAt,
        Instant updatedAt) {
}
