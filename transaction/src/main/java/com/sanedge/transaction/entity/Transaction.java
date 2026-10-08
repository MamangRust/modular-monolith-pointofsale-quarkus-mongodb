package com.sanedge.transaction.entity;

import org.bson.types.ObjectId;

import com.sanedge.common.entity.BaseModel;
import com.sanedge.common.enums.PaymentStatus;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "transactions")
public class Transaction extends BaseModel {

    private String orderId;

    private ObjectId merchantId;

    private String paymentMethod;

    private Integer amount;

    private Integer changeAmount = 0;

    private PaymentStatus status = PaymentStatus.PENDING;

    private String idempotencyKey;

    public String getTransactionId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setTransactionId(String transactionId) {
        this.id = transactionId != null ? new ObjectId(transactionId) : null;
    }
}
