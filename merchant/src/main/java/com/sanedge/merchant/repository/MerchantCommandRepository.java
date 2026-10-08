package com.sanedge.merchant.repository;

import java.time.Instant;

import com.sanedge.merchant.entity.Merchant;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class MerchantCommandRepository implements ReactivePanacheMongoRepository<Merchant> {

    public Uni<Merchant> trashed(String merchantId) {
        return find("_id", new ObjectId(merchantId)).firstResult()
                .chain(merchant -> {
                    if (merchant != null && merchant.getDeletedAt() == null) {
                        merchant.setDeletedAt(Instant.now());
                        return merchant.update().map(v -> merchant);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Merchant> restore(String merchantId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantId)).firstResult()
                .chain(merchant -> {
                    if (merchant != null) {
                        merchant.setDeletedAt(null);
                        return merchant.update().map(v -> merchant);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Boolean> deletePermanent(String merchantId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantId)).firstResult()
                .chain(merchant -> {
                    if (merchant != null) {
                        return merchant.delete().map(v -> true);
                    }
                    return Uni.createFrom().item(false);
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

    public Uni<Boolean> updateStatus(String merchantId, String status) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(merchantId)).firstResult()
                .chain(merchant -> {
                    if (merchant != null) {
                        try {
                            merchant.status = com.sanedge.common.enums.Status.valueOf(status.toUpperCase());
                            return merchant.update().map(v -> true);
                        } catch (IllegalArgumentException e) {
                            return Uni.createFrom().item(false);
                        }
                    }
                    return Uni.createFrom().item(false);
                });
    }
}
