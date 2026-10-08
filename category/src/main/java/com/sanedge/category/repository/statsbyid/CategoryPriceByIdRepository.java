package com.sanedge.category.repository.statsbyid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.sanedge.category.entity.CategoryMonthPrice;
import com.sanedge.category.entity.CategoryYearPrice;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.sanedge.category.entity.Category;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;


@ApplicationScoped
public class CategoryPriceByIdRepository {
    @Inject
    ReactiveMongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "POINT_OF_SALE")
    String mongoDatabaseName;


    private List<Bson> basePipeline(Date start, Date end, Bson orderFilter, Bson categoryFilter) {
        List<Bson> matchFilters = new ArrayList<>();
        matchFilters.add(Filters.eq("deletedAt", null));
        matchFilters.add(Filters.gte("createdAt", start));
        matchFilters.add(Filters.lt("createdAt", end));
        if (orderFilter != null) {
            matchFilters.add(orderFilter);
        }

        List<Bson> stages = new ArrayList<>();
        stages.add(Aggregates.match(Filters.and(matchFilters)));
        stages.add(Aggregates.lookup("order_items", "_id", "orderId", "items"));
        stages.add(Aggregates.unwind("$items"));
        stages.add(Aggregates.match(Filters.eq("items.deletedAt", null)));
        stages.add(Aggregates.lookup("products", "items.productId", "_id", "product"));
        stages.add(Aggregates.unwind("$product"));
        stages.add(Aggregates.match(Filters.eq("product.deletedAt", null)));
        stages.add(Aggregates.lookup("categories", "product.categoryId", "_id", "category"));
        stages.add(Aggregates.unwind("$category"));
        stages.add(Aggregates.match(categoryFilter != null
                ? Filters.and(Filters.eq("category.deletedAt", null), categoryFilter)
                : Filters.eq("category.deletedAt", null)));
        return stages;
    }

    private Bson monthGroup() {
        Document groupId = new Document("month",
                new Document("$dateToString", new Document("format", "%b").append("date", "$createdAt")))
                .append("monthIndex", new Document("$month", "$createdAt"))
                .append("categoryId", "$category._id")
                .append("categoryName", "$category.name");
        return Aggregates.group(groupId,
                Accumulators.addToSet("orderIds", "$_id"),
                Accumulators.sum("itemsSold", "$items.quantity"),
                Accumulators.sum("totalRevenue", "$totalPrice"));
    }

    private Bson yearGroup() {
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                .append("categoryId", "$category._id")
                .append("categoryName", "$category.name");
        return Aggregates.group(groupId,
                Accumulators.addToSet("orderIds", "$_id"),
                Accumulators.sum("itemsSold", "$items.quantity"),
                Accumulators.sum("totalRevenue", "$totalPrice"),
                Accumulators.addToSet("productIds", "$items.productId"));
    }


    private Uni<List<Document>> runAggregate(List<Bson> pipeline) {
        return Multi.createFrom().publisher(mongoClient.getDatabase(mongoDatabaseName).getCollection("orders").aggregate(pipeline))
                .collect().asList();
    }


    public Uni<List<CategoryMonthPrice>> findMonthlyCategoryPriceById(String categoryId, Integer startDate) {
        List<Bson> pipeline = basePipeline(
                Date.from(Instant.parse(startDate + "-01-01T00:00:00Z")),
                Date.from(Instant.parse((startDate + 1) + "-01-01T00:00:00Z")),
                null, Filters.eq("category._id", new ObjectId(categoryId)));
        pipeline.add(monthGroup());
        pipeline.add(new Document("$sort", new Document("_id.monthIndex", 1).append("totalRevenue", -1)));

        return runAggregate(pipeline)

                .map(docs -> {
                    List<CategoryMonthPrice> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new CategoryMonthPrice(
                                id.getString("month"),
                                id.get("categoryId", ObjectId.class) != null ? id.get("categoryId", ObjectId.class).toHexString() : null,
                                id.getString("categoryName"),
                                doc.getList("orderIds", ObjectId.class) != null ? doc.getList("orderIds", ObjectId.class).size() : 0,
                                doc.getInteger("itemsSold", 0),
                                ((Number) doc.getOrDefault("totalRevenue", 0)).longValue()));
                    }
                    return list;
                });

    }

    public Uni<List<CategoryYearPrice>> findYearlyCategoryPriceById(String categoryId, Integer startDate) {
        List<Bson> pipeline = basePipeline(
                Date.from(Instant.parse((startDate - 4) + "-01-01T00:00:00Z")),
                Date.from(Instant.parse((startDate + 1) + "-01-01T00:00:00Z")),
                null, Filters.eq("category._id", new ObjectId(categoryId)));
        pipeline.add(yearGroup());
        pipeline.add(new Document("$sort", new Document("_id.year", 1).append("totalRevenue", -1)));

        return runAggregate(pipeline)

                .map(docs -> {
                    List<CategoryYearPrice> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new CategoryYearPrice(
                                id.getString("year"),
                                id.get("categoryId", ObjectId.class) != null ? id.get("categoryId", ObjectId.class).toHexString() : null,
                                id.getString("categoryName"),
                                doc.getList("orderIds", ObjectId.class) != null ? doc.getList("orderIds", ObjectId.class).size() : 0,
                                doc.getInteger("itemsSold", 0),
                                ((Number) doc.getOrDefault("totalRevenue", 0)).longValue(),
                                doc.getList("productIds", ObjectId.class) != null ? doc.getList("productIds", ObjectId.class).size() : 0));
                    }
                    return list;
                });

    }
}
