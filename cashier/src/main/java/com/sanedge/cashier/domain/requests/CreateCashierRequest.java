package com.sanedge.cashier.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "CreateCashierRequest", description = "Request untuk membuat cashier baru")
public class CreateCashierRequest {
    @NotNull
    @Schema(description = "ID merchant", example = "123")
    private String merchantId;

    @NotNull
    @Schema(description = "ID user", example = "789")
    private String userId;

    @NotBlank
    @Schema(description = "Nama cashier", example = "John Doe")
    private String name;
}
