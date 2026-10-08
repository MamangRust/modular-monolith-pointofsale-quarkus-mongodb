package com.sanedge.merchant.domain.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMerchantDocumentRequest {
    @NotBlank(message = "documentId wajib diisi")
    private String documentId;

    @NotBlank(message = "merchantId wajib diisi")
    private String merchantId;

    @NotBlank(message = "Document type wajib diisi")
    private String documentType;

    @NotBlank(message = "Document url wajib diisi")
    private String documentUrl;

    private String note;

    @NotBlank(message = "Status wajib diisi")
    private String status;
}
