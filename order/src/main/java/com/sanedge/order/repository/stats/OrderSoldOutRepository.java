package com.sanedge.order.repository.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.sanedge.order.entity.OrderMonth;
import com.sanedge.order.entity.OrderYear;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OrderSoldOutRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "POINT_OF_SALE")
    String mongoDatabaseName;

    private List<Bson> basePipeline(Date start, Date end, Bson orderFilter) {
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
        return stages;
    }

    private Uni<List<Document>> runAggregate(List<Bson> pipeline) {
        return Multi.createFrom()
                .publisher(mongoClient.getDatabase(mongoDatabaseName).getCollection("orders").aggregate(pipeline))
                .collect().asList();
    }

    private List<OrderMonth> mapMonthly(List<Document> docs) {
        List<OrderMonth> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new OrderMonth(
                    id.getString("month"),
                    doc.getList("orderIds", org.bson.types.ObjectId.class) != null
                            ? doc.getList("orderIds", org.bson.types.ObjectId.class).size()
                            : 0,
                    ((Number) doc.getOrDefault("totalRevenue", 0)).longValue(),
                    doc.getInteger("itemsSold", 0)));
        }
        return list;
    }

    private List<OrderYear> mapYearly(List<Document> docs) {
        List<OrderYear> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new OrderYear(
                    id.getString("year"),
                    doc.getList("orderIds", org.bson.types.ObjectId.class) != null
                            ? doc.getList("orderIds", org.bson.types.ObjectId.class).size()
                            : 0,
                    ((Number) doc.getOrDefault("totalRevenue", 0)).longValue(),
                    doc.getInteger("itemsSold", 0),
                    doc.getList("cashierIds", org.bson.types.ObjectId.class) != null
                            ? doc.getList("cashierIds", org.bson.types.ObjectId.class).size()
                            : 0,
                    doc.getList("productIds", org.bson.types.ObjectId.class) != null
                            ? doc.getList("productIds", org.bson.types.ObjectId.class).size()
                            : 0));
        }
        return list;
    }

    public Uni<List<OrderMonth>> findMonthlyOrdersByYear(Integer yearMonth) {
        int year = yearMonth / 100;
        Date start = Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", year, yearMonth % 100)));
        Date end = Date.from(Instant.parse((year + 1) + "-01-01T00:00:00Z"));

        List<Bson> pipeline = basePipeline(start, end, null);
        Document groupId = new Document("month",
                new Document("$dateToString", new Document("format", "%b").append("date", "$createdAt")))
                .append("monthIndex", new Document("$month", "$createdAt"));
        pipeline.add(Aggregates.group(groupId,
                Accumulators.addToSet("orderIds", "$_id"),
                Accumulators.sum("totalRevenue", "$totalPrice"),
                Accumulators.sum("itemsSold", "$items.quantity")));
        pipeline.add(new Document("$sort", new Document("_id.monthIndex", 1)));

        return runAggregate(pipeline).map(this::mapMonthly);
    }

    public Uni<List<OrderYear>> findYearlyOrders(Integer yearMonth) {
        int year = yearMonth / 100;
        Date start = Date.from(Instant.parse((year - 4) + "-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse((year + 1) + "-01-01T00:00:00Z"));

        List<Bson> pipeline = basePipeline(start, end, null);
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")));
        pipeline.add(Aggregates.group(groupId,
                Accumulators.addToSet("orderIds", "$_id"),
                Accumulators.sum("totalRevenue", "$totalPrice"),
                Accumulators.sum("itemsSold", "$items.quantity"),
                Accumulators.addToSet("cashierIds", "$cashierId"),
                Accumulators.addToSet("productIds", "$items.productId")));
        pipeline.add(new Document("$sort", new Document("_id.year", 1)));

        return runAggregate(pipeline).map(this::mapYearly);
    }
}
