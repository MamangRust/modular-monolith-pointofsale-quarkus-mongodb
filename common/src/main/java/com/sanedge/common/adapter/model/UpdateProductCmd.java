package com.sanedge.common.adapter.model;

/**
 * Command to update a product in the product service.
 */
public record UpdateProductCmd(
        String productId,
        String merchantId,
        String categoryId,
        String name,
        String description,
        int price,
        int countInStock,
        String brand,
        int weight,
        String imageProduct) {
}
