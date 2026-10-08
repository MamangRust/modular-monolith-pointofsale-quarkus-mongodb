package com.sanedge.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.transaction.entity.Outbox;
import com.sanedge.transaction.entity.OutboxStatus;
import com.sanedge.transaction.repository.OutboxRepository;

import io.opentelemetry.api.OpenTelemetry;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final String OUTBOX_ID_1 = "507f1f77bcf86cd799439011";
    private static final String OUTBOX_ID_2 = "507f1f77bcf86cd799439012";
    private static final ObjectId OBJ_ID_1 = new ObjectId(OUTBOX_ID_1);
    private static final ObjectId OBJ_ID_2 = new ObjectId(OUTBOX_ID_2);

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

    private Outbox pendingOutbox(String hexId, String topic) {
        Outbox outbox = new Outbox();
        outbox.id = new ObjectId(hexId);
        outbox.setAggregateType("TRANSACTION");
        outbox.setAggregateId(hexId);
        outbox.setTopic(topic);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setPayload(new JsonObject().put("email", "test@example.com").encode());
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
            Outbox row = pendingOutbox(OUTBOX_ID_1, "email-service-topic-transaction-create");
            when(outboxRepository.findPending(10)).thenReturn(Uni.createFrom().item(List.of(row)));
            when(kafkaService.sendMessage(eq(row.getTopic()), eq(OUTBOX_ID_1), any(JsonObject.class)))
                    .thenReturn(Uni.createFrom().voidItem());
            when(outboxRepository.markProcessed(OBJ_ID_1)).thenReturn(Uni.createFrom().voidItem());

            publisher.drainPending().await().indefinitely();

            verify(kafkaService).sendMessage(eq(row.getTopic()), eq(OUTBOX_ID_1), any(JsonObject.class));
            verify(outboxRepository).markProcessed(OBJ_ID_1);
            verify(outboxRepository, never()).markFailed(any(), any(), anyInt());
        }

        @Test
        void publishFailure_marksFailedForRetry() {
            Outbox row = pendingOutbox(OUTBOX_ID_2, "email-service-topic-transaction-create");
            row.setAttempts(1); // will become 2 of max 3 → stays PENDING for retry
            when(outboxRepository.findPending(10)).thenReturn(Uni.createFrom().item(List.of(row)));
            when(kafkaService.sendMessage(eq(row.getTopic()), eq(OUTBOX_ID_2), any(JsonObject.class)))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("broker down")));
            when(outboxRepository.markFailed(OBJ_ID_2, "broker down", 3)).thenReturn(Uni.createFrom().voidItem());

            publisher.drainPending().await().indefinitely();

            verify(outboxRepository).markFailed(OBJ_ID_2, "broker down", 3);
            verify(outboxRepository, never()).markProcessed(any());
        }

        @Test
        void processesMultipleRowsSequentially() {
            Outbox first = pendingOutbox(OUTBOX_ID_1, "email-service-topic-transaction-create");
            Outbox second = pendingOutbox(OUTBOX_ID_2, "email-service-topic-transaction-create");
            when(outboxRepository.findPending(10)).thenReturn(Uni.createFrom().item(List.of(first, second)));
            when(kafkaService.sendMessage(anyString(), anyString(), any(JsonObject.class)))
                    .thenReturn(Uni.createFrom().voidItem());
            when(outboxRepository.markProcessed(any())).thenReturn(Uni.createFrom().voidItem());

            publisher.drainPending().await().indefinitely();

            verify(outboxRepository).markProcessed(OBJ_ID_1);
            verify(outboxRepository).markProcessed(OBJ_ID_2);
        }
    }
}
