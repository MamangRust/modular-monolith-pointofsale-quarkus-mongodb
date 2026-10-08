package com.sanedge.product.entity;

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
@MongoEntity(collection = "products")
public class Product extends BaseModel {

    private ObjectId merchantId;

    private ObjectId categoryId;

    private String name;

    private String description;

    private Integer price;

    private Integer countInStock = 0;

    private String brand;
    private Integer weight;

    private String slugProduct;

    private String imageProduct;

    private String barcode;

    public String getProductId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setProductId(String productId) {
        this.id = productId != null ? new ObjectId(productId) : null;
    }
}
