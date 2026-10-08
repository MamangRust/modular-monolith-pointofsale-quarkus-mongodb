package com.sanedge.common.test;

import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import jakarta.inject.Inject;

/**
 * Base class for {@code @QuarkusTest} repository tests.
 *
 * <p>Unlike the previous PostgreSQL/Hibernate Reactive setup, MongoDB does not
 * roll back test data when a test finishes. All repository tests in a module
 * share the same database (and Quarkus reuses the application across test
 * classes), so without an explicit reset data from one test leaks into the
 * next. This base class wipes every collection before each test method, while
 * leaving indexes intact.
 */
public abstract class MongoTestBase {

    @Inject
    ReactiveMongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "test")
    String databaseName;

    @BeforeEach
    void resetMongoState() {
        ReactiveMongoDatabase database = mongoClient.getDatabase(databaseName);
        database.listCollectionNames()
                .onItem().transformToUniAndConcatenate(
                        name -> database.getCollection(name).deleteMany(new Document()))
                .collect().last()
                .await().indefinitely();
    }
}
