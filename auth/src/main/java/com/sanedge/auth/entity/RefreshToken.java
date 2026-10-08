package com.sanedge.auth.entity;

import com.sanedge.common.entity.BaseModel;

import java.time.Instant;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "refresh_tokens")
public class RefreshToken extends BaseModel {
    private ObjectId userId;

    private String token;

    private Instant expiration;

    public ObjectId getUserId() {
        return userId;
    }

    public void setUserId(ObjectId userId) {
        this.userId = userId;
    }
}
