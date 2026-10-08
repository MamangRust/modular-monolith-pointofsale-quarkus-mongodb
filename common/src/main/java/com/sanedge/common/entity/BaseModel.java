package com.sanedge.common.entity;

import java.time.Instant;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseModel extends ReactivePanacheMongoEntity {
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
