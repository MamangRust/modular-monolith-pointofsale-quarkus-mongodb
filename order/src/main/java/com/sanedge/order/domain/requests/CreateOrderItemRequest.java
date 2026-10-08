package com.sanedge.order.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Item order baru")
public class CreateOrderItemRequest {
    @NotNull
    @Schema(description = "ID produk", example = "507f1f77bcf86cd799439011")
    private String productId;

    @NotNull
    @Min(1)
    @Schema(description = "Jumlah produk", example = "2")
    private Integer quantity;

    @Schema(description = "Harga per item (optional — falls back to product price)", example = "50000")
    private Integer price;
}
