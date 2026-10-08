package com.sanedge.merchant.entity;

import java.util.UUID;
import com.sanedge.common.entity.BaseModel;
import com.sanedge.common.enums.Status;

import org.bson.types.ObjectId;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "merchants")
public class Merchant extends BaseModel {

    public UUID merchantNo;

    public String name;

    public String apiKey;

    public ObjectId userId;

    public Status status = Status.PENDING;

    public String getMerchantId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setMerchantId(String merchantId) {
        this.id = merchantId != null ? new ObjectId(merchantId) : null;
    }
}
