package com.sanedge.merchant.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request untuk update status merchant")
public class UpdateMerchantStatus {
    @NotBlank(message = "merchant_id wajib diisi")
    private String merchantId;

    @NotBlank(message = "Status wajib diisi")
    private String status;
}
