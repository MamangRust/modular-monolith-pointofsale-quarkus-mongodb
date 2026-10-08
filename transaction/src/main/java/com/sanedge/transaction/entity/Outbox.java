package com.sanedge.transaction.entity;

import java.time.Instant;

import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "outbox")
public class Outbox extends ReactivePanacheMongoEntity {

    private String aggregateType;

    private String aggregateId;

    private String topic;

    private String payload;

    private OutboxStatus status = OutboxStatus.PENDING;

    private Integer attempts = 0;

    private Instant createdAt;

    private Instant processedAt;

    private String lastError;

    public void onCreate() {
        this.createdAt = Instant.now();
    }
}
