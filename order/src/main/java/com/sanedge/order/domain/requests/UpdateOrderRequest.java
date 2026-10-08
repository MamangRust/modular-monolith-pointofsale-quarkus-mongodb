package com.sanedge.order.domain.requests;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request untuk memperbarui order")
public class UpdateOrderRequest {
    @NotNull
    @Schema(description = "ID order yang akan diperbarui", example = "507f1f77bcf86cd799439011")
    private String orderId;

    @NotNull
    @Schema(description = "ID cashier yang membuat order", example = "507f1f77bcf86cd799439012")
    private String cashierId;

    @Valid
    @NotNull
    @Schema(description = "Daftar item order yang diperbarui")
    private List<UpdateOrderItemRequest> items;
}
