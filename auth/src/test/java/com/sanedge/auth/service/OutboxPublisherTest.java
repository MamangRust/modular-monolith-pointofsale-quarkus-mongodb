package com.sanedge.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.auth.entity.Outbox;
import com.sanedge.auth.entity.OutboxStatus;
import com.sanedge.auth.repository.OutboxRepository;

import io.opentelemetry.api.OpenTelemetry;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxRepository outboxRepository;
    @Mock private KafkaService kafkaService;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(OpenTelemetry.noop());
        publisher.outboxRepository = outboxRepository;
        publisher.kafkaService = kafkaService;
        publisher.maxAttempts = 3;
        publisher.batchSize = 10;
    }

    private final org.bson.types.ObjectId oid = org.bson.types.ObjectId.get();
    private final org.bson.types.ObjectId oid2 = org.bson.types.ObjectId.get();

    private Outbox pendingOutbox(org.bson.types.ObjectId id, String topic) {
        Outbox outbox = new Outbox();
        outbox.id = id;
        outbox.setAggregateType("USER");
        outbox.setAggregateId(id.toHexString());
        outbox.setTopic(topic);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setPayload(new JsonObject().put("email", "user@example.com").encode());
        return outbox;
    }

    @Nested
    @DisplayName("drainPending tests")
    class DrainTests {

        @Test
        void noPendingRows_doesNothing() {
            when(outboxRepository.findPending(10)).thenReturn(Uni.createFrom().item(List.of()));

            publisher.drainPending().await().indefinitely();

            verify(outboxRepository, never()).markProcessed(any());
            verify(kafkaService, never()).sendMessage(anyString(), anyString(), any(JsonObject.class));
        }

        @Test
        void publishesAndMarksProcessed() {
            Outbox row = pendingOutbox(oid, "email-service-topic-auth-register");
            when(outboxRepository.findPending(10)).thenReturn(Uni.createFrom().item(List.of(row)));
            when(kafkaService.sendMessage(eq(row.getTopic()), eq(oid.toHexString()), any(JsonObject.class)))
                    .thenReturn(Uni.createFrom().voidItem());
            when(outboxRepository.markProcessed(oid)).thenReturn(Uni.createFrom().voidItem());

            publisher.drainPending().await().indefinitely();

            verify(kafkaService).sendMessage(eq(row.getTopic()), eq(oid.toHexString()), any(JsonObject.class));
            verify(outboxRepository).markProcessed(oid);
            verify(outboxRepository, never()).markFailed(any(), any(), anyInt());
        }

        @Test
        void publishFailure_marksFailedForRetry() {
            Outbox row = pendingOutbox(oid2, "email-service-topic-auth-forgot-password");
            row.setAttempts(1); // will become 2 of max 3 → stays PENDING for retry
            when(outboxRepository.findPending(10)).thenReturn(Uni.createFrom().item(List.of(row)));
            when(kafkaService.sendMessage(eq(row.getTopic()), eq(oid2.toHexString()), any(JsonObject.class)))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("broker down")));
            when(outboxRepository.markFailed(oid2, "broker down", 3)).thenReturn(Uni.createFrom().voidItem());

            publisher.drainPending().await().indefinitely();

            verify(outboxRepository).markFailed(oid2, "broker down", 3);
            verify(outboxRepository, never()).markProcessed(any());
        }
    }
}
