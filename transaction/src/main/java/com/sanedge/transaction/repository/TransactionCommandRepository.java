package com.sanedge.transaction.repository;

import java.time.Instant;

import com.sanedge.transaction.entity.Transaction;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class TransactionCommandRepository implements ReactivePanacheMongoRepository<Transaction> {

    public Uni<Transaction> trashed(String transactionId) {
        return find("_id", new ObjectId(transactionId)).firstResult()
                .chain(transaction -> {
                    if (transaction != null && transaction.getDeletedAt() == null) {
                        transaction.setDeletedAt(Instant.now());
                        return transaction.update().map(v -> transaction);
                    }
                    return Uni.createFrom().item(transaction);
                });
    }

    public Uni<Transaction> restore(String transactionId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(transactionId)).firstResult()
                .chain(transaction -> {
                    if (transaction != null) {
                        transaction.setDeletedAt(null);
                        return transaction.update().map(v -> transaction);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Transaction> deletePermanent(String transactionId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(transactionId)).firstResult()
                .chain(transaction -> {
                    if (transaction != null) {
                        return transaction.delete().map(v -> transaction);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Boolean> restoreAllDeleted() {
        return update("{ '$set': { 'deletedAt': null } }")
                .where("{ 'deletedAt': { '$ne': null } }")
                .map(updatedCount -> updatedCount > 0);
    }

    public Uni<Boolean> deleteAllDeleted() {
        return delete("{ 'deletedAt': { '$ne': null } }")
                .map(deletedCount -> deletedCount > 0);
    }
}
