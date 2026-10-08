package com.sanedge.merchant.entity;

import com.sanedge.common.entity.BaseModel;

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
@MongoEntity(collection = "merchant_documents")
public class MerchantDocument extends BaseModel {

    public ObjectId merchantId;

    public String documentType;

    public String documentUrl;

    public String status = "PENDING";

    public String note;

    public String getDocumentId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setDocumentId(String documentId) {
        this.id = documentId != null ? new ObjectId(documentId) : null;
    }
}
