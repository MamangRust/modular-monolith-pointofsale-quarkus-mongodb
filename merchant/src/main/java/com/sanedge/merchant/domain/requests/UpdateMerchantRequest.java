package com.sanedge.merchant.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request untuk update merchant")
public class UpdateMerchantRequest {
    private String merchantId;

    @NotBlank(message = "Nama merchant wajib diisi")
    private String name;

    private String userId;

    @NotBlank(message = "Status wajib diisi")
    private String status;
}
