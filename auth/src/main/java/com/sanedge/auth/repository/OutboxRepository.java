package com.sanedge.auth.repository;

import java.time.Instant;
import java.util.List;

import com.sanedge.auth.entity.Outbox;
import com.sanedge.auth.entity.OutboxStatus;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OutboxRepository implements ReactivePanacheMongoRepository<Outbox> {

    public Uni<List<Outbox>> findPending(int limit) {
        return find("{ 'status': ?1 }", OutboxStatus.PENDING)
                .page(io.quarkus.panache.common.Page.ofSize(limit))
                .list();
    }

    public Uni<Void> markProcessed(ObjectId id) {
        return findById(id)
                .chain(outbox -> {
                    if (outbox == null) {
                        return Uni.createFrom().voidItem();
                    }
                    outbox.setStatus(OutboxStatus.PROCESSED);
                    outbox.setProcessedAt(Instant.now());
                    outbox.setLastError(null);
                    return outbox.update().replaceWithVoid();
                });
    }

    /**
     * Records a delivery failure. Attempts counter is incremented; when it
     * reaches {@code maxAttempts} the document is moved to FAILED (dead letter),
     * otherwise it stays PENDING so the publisher retries it on the next tick.
     */
    public Uni<Void> markFailed(ObjectId id, String error, int maxAttempts) {
        return findById(id)
                .chain(outbox -> {
                    if (outbox == null) {
                        return Uni.createFrom().voidItem();
                    }
                    int attempts = outbox.getAttempts() + 1;
                    outbox.setAttempts(attempts);
                    outbox.setLastError(error);
                    if (attempts >= maxAttempts) {
                        outbox.setStatus(OutboxStatus.FAILED);
                        outbox.setProcessedAt(Instant.now());
                    }
                    return outbox.update().replaceWithVoid();
                });
    }
}
