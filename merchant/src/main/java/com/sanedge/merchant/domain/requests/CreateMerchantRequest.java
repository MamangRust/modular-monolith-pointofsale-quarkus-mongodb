package com.sanedge.merchant.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request untuk membuat merchant")
public class CreateMerchantRequest {
    @NotBlank(message = "Nama merchant wajib diisi")
    private String name;

    @NotBlank(message = "User ID wajib diisi")
    private String userId;
}
