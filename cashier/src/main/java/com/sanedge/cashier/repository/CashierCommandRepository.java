package com.sanedge.cashier.repository;

import java.time.Instant;

import com.sanedge.cashier.entity.Cashier;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class CashierCommandRepository implements ReactivePanacheMongoRepository<Cashier> {

    public Uni<Cashier> trashed(String cashierId) {
        return find("_id", new ObjectId(cashierId)).firstResult()
                .chain(cashier -> {
                    if (cashier != null && cashier.getDeletedAt() == null) {
                        cashier.setDeletedAt(Instant.now());
                        return cashier.update().map(v -> cashier);
                    }
                    return Uni.createFrom().item(cashier);
                });
    }

    public Uni<Cashier> restore(String cashierId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(cashierId)).firstResult()
                .chain(cashier -> {
                    if (cashier != null) {
                        cashier.setDeletedAt(null);
                        return cashier.update().map(v -> cashier);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Cashier> deletePermanent(String cashierId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(cashierId)).firstResult()
                .chain(cashier -> {
                    if (cashier != null) {
                        return cashier.delete().map(v -> cashier);
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
