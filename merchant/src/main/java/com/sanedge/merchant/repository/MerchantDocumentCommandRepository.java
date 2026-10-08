package com.sanedge.merchant.repository;

import java.time.Instant;

import com.sanedge.merchant.entity.MerchantDocument;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class MerchantDocumentCommandRepository implements ReactivePanacheMongoRepository<MerchantDocument> {

    public Uni<MerchantDocument> trashed(String documentId) {
        return find("_id", new ObjectId(documentId)).firstResult()
                .chain(doc -> {
                    if (doc != null && doc.getDeletedAt() == null) {
                        doc.setDeletedAt(Instant.now());
                        return doc.update().map(v -> doc);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantDocument> restore(String documentId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(documentId)).firstResult()
                .chain(doc -> {
                    if (doc != null) {
                        doc.setDeletedAt(null);
                        return doc.update().map(v -> doc);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Boolean> deletePermanent(String documentId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(documentId)).firstResult()
                .chain(doc -> {
                    if (doc != null) {
                        return doc.delete().map(v -> true);
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
}
