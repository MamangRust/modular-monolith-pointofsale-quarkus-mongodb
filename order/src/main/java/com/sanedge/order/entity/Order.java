package com.sanedge.order.entity;

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
@MongoEntity(collection = "orders")
public class Order extends BaseModel {

    private ObjectId merchantId;

    private ObjectId cashierId;

    private Long totalPrice;

    public String getOrderId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setOrderId(String orderId) {
        this.id = orderId != null ? new ObjectId(orderId) : null;
    }
}
