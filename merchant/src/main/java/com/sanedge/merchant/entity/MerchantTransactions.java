package com.sanedge.merchant.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantTransactions {
    private String transactionId;
    private String cardNumber;
    private Long amount;
    private String paymentMethod;
    private String merchantId;
    private String merchantName;
    private Instant transactionTime;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
