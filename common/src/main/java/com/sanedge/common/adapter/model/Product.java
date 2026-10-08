package com.sanedge.common.adapter.model;

import java.time.Instant;

/**
 * Domain view of a product owned by the product service.
 */
public record Product(
        String id,
        String merchantId,
        String categoryId,
        String name,
        String description,
        int price,
        int countInStock,
        String brand,
        int weight,
        float rating,
        String slugProduct,
        String imageProduct,
        String barcode,
        Instant createdAt,
        Instant updatedAt) {
}
