package com.sanedge.cashier.entity;

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
@MongoEntity(collection = "cashiers")
public class Cashier extends BaseModel {

    private ObjectId merchantId;

    private ObjectId userId;

    private String name;

    public String getCashierId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setCashierId(String cashierId) {
        this.id = cashierId != null ? new ObjectId(cashierId) : null;
    }
}
