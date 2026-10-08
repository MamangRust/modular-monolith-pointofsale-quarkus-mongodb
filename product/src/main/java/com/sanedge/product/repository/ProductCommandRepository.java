package com.sanedge.product.repository;

import java.time.Instant;

import com.sanedge.product.entity.Product;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class ProductCommandRepository implements ReactivePanacheMongoRepository<Product> {

    public Uni<Product> trashed(String productId) {
        return find("_id", new ObjectId(productId)).firstResult()
                .chain(product -> {
                    if (product != null && product.getDeletedAt() == null) {
                        product.setDeletedAt(Instant.now());
                        return product.update().map(v -> product);
                    }
                    return Uni.createFrom().item(product);
                });
    }

    public Uni<Product> restore(String productId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(productId)).firstResult()
                .chain(product -> {
                    if (product != null) {
                        product.setDeletedAt(null);
                        return product.update().map(v -> product);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Product> deletePermanent(String productId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(productId)).firstResult()
                .chain(product -> {
                    if (product != null) {
                        return product.delete().map(v -> product);
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
