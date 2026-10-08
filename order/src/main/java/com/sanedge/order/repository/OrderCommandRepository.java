package com.sanedge.order.repository;

import java.time.Instant;

import com.sanedge.order.entity.Order;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OrderCommandRepository implements ReactivePanacheMongoRepository<Order> {

    public Uni<Order> trashed(String orderId) {
        return find("_id", new ObjectId(orderId)).firstResult()
                .chain(order -> {
                    if (order != null && order.getDeletedAt() == null) {
                        order.setDeletedAt(Instant.now());
                        return order.update().map(v -> order);
                    }
                    return Uni.createFrom().item(order);
                });
    }

    public Uni<Order> restore(String orderId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderId)).firstResult()
                .chain(order -> {
                    if (order != null) {
                        order.setDeletedAt(null);
                        return order.update().map(v -> order);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Order> deletePermanent(String orderId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderId)).firstResult()
                .chain(order -> {
                    if (order != null) {
                        return order.delete().map(v -> order);
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
