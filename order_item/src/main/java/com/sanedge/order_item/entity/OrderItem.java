package com.sanedge.order_item.entity;

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
@MongoEntity(collection = "order_items")
public class OrderItem extends BaseModel {

    private ObjectId orderId;

    private ObjectId productId;

    private Integer quantity;

    private Integer price;

    public String getOrderItemId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setOrderItemId(String orderItemId) {
        this.id = orderItemId != null ? new ObjectId(orderItemId) : null;
    }
}
