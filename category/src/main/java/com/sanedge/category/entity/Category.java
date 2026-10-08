package com.sanedge.category.entity;

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
@MongoEntity(collection = "categories")
public class Category extends BaseModel {

    private String name;

    private String description;

    private String slugCategory;

    public String getCategoryId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setCategoryId(String categoryId) {
        this.id = categoryId != null ? new ObjectId(categoryId) : null;
    }
}
