package com.sanedge.order.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request untuk membuat order baru")
public class CreateOrderRequest {
    @NotNull
    @Schema(description = "ID merchant", example = "507f1f77bcf86cd799439011")
    private String merchantId;

    @NotNull
    @Schema(description = "ID cashier yang membuat order", example = "507f1f77bcf86cd799439012")
    private String cashierId;

    @Valid
    @NotNull
    @Schema(description = "Daftar item order")
    private List<CreateOrderItemRequest> items;
}
