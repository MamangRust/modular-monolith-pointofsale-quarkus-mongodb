package com.sanedge.transaction.config;

import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Creates the indexes the transaction module relies on but that the removed
 * Flyway migrations used to create.
 *
 * <p>The idempotency index replaces {@code idx_transactions_idempotency_key_active}
 * from {@code V10__Add_transaction_idempotency.sql}. It is a <em>partial</em>
 * unique index: only active rows ({@code deletedAt == null}) that carry a key
 * are constrained, so a soft-deleted transaction never blocks a new one that
 * reuses the same key.
 */
@ApplicationScoped
public class TransactionIndexInitializer {

    private static final Logger LOG = Logger.getLogger(TransactionIndexInitializer.class);

    @Inject
    ReactiveMongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    void onStart(@Observes StartupEvent event) {
        Bson partialFilter = Filters.and(
                Filters.exists("idempotencyKey"),
                Filters.eq("deletedAt", null));
        IndexOptions options = new IndexOptions()
                .name("idx_transactions_idempotency_key_active")
                .unique(true)
                .partialFilterExpression(partialFilter);

        try {
            String name = mongoClient.getDatabase(databaseName)
                    .getCollection("transactions")
                    .createIndex(Indexes.ascending("idempotencyKey"), options)
                    .await().indefinitely();
            LOG.infof("✅ Mongo index ready | name=%s database=%s", name, databaseName);
        } catch (RuntimeException e) {
            LOG.errorf(e, "❌ Failed to create transaction idempotency index | database=%s", databaseName);
        }
    }
}
