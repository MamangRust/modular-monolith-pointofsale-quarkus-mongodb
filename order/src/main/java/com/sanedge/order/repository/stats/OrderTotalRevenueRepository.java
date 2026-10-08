package com.sanedge.order.repository.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.sanedge.order.domain.requests.FindOrderMonthRange;
import com.sanedge.order.entity.OrderMonthTotalRevenue;
import com.sanedge.order.entity.OrderYearTotalRevenue;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OrderTotalRevenueRepository {

    private static final String[] MONTH_NAMES = { "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December" };

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

    private void ensureMonth(List<OrderMonthTotalRevenue> list, String year, String monthName) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()) && monthName.equals(r.getMonth()));
        if (!exists) {
            list.add(new OrderMonthTotalRevenue(year, monthName, 0));
        }
    }

    private void ensureYear(List<OrderYearTotalRevenue> list, String year) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()));
        if (!exists) {
            list.add(new OrderYearTotalRevenue(year, 0));
        }
    }

    public Uni<List<OrderMonthTotalRevenue>> findMonthlyTotalRevenue(FindOrderMonthRange req) {
        int startYear = req.getStartYear();
        int startMonth = req.getStartMonth();
        int endYear = req.getEndYear();
        int endMonth = req.getEndMonth();
        String startIso = String.format("%04d-%02d-01T00:00:00Z", startYear, startMonth);
        int endNextYear = endMonth == 12 ? endYear + 1 : endYear;
        int endNextMonth = endMonth == 12 ? 1 : endMonth + 1;
        String endIso = String.format("%04d-%02d-01T00:00:00Z", endNextYear, endNextMonth);
        Date start = Date.from(Instant.parse(startIso));
        Date end = Date.from(Instant.parse(endIso));

        List<Bson> pipeline = basePipeline(start, end, null);
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                .append("monthIndex", new Document("$month", "$createdAt"));
        pipeline.add(Aggregates.group(groupId, Accumulators.sum("totalRevenue", "$totalPrice")));

        return runAggregate(pipeline)
                .map(docs -> {
                    List<OrderMonthTotalRevenue> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new OrderMonthTotalRevenue(
                                id.getString("year"),
                                MONTH_NAMES[id.getInteger("monthIndex", 1) - 1],
                                (int) ((Number) doc.getOrDefault("totalRevenue", 0)).longValue()));
                    }
                    ensureMonth(list, String.valueOf(startYear), MONTH_NAMES[startMonth - 1]);
                    if (!String.valueOf(endYear).equals(String.valueOf(startYear))
                            || !String.valueOf(endMonth).equals(String.valueOf(startMonth))) {
                        ensureMonth(list, String.valueOf(endYear), MONTH_NAMES[endMonth - 1]);
                    }
                    list.sort((a, b) -> {
                        int byYear = b.getYear().compareTo(a.getYear());
                        if (byYear != 0) {
                            return byYear;
                        }
                        int am = java.util.Arrays.asList(MONTH_NAMES).indexOf(a.getMonth()) + 1;
                        int bm = java.util.Arrays.asList(MONTH_NAMES).indexOf(b.getMonth()) + 1;
                        return Integer.compare(bm, am);
                    });
                    return list;
                });
    }

    public Uni<List<OrderYearTotalRevenue>> findYearlyTotalRevenue(Integer year) {
        int yearMinusOne = year - 1;
        Date start = Date.from(Instant.parse(yearMinusOne + "-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse((year + 1) + "-01-01T00:00:00Z"));

        List<Bson> pipeline = basePipeline(start, end, null);
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")));
        pipeline.add(Aggregates.group(groupId, Accumulators.sum("totalRevenue", "$totalPrice")));

        return runAggregate(pipeline)
                .map(docs -> {
                    List<OrderYearTotalRevenue> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new OrderYearTotalRevenue(
                                id.getString("year"),
                                (int) ((Number) doc.getOrDefault("totalRevenue", 0)).longValue()));
                    }
                    ensureYear(list, String.valueOf(year));
                    ensureYear(list, String.valueOf(yearMinusOne));
                    list.sort((a, b) -> b.getYear().compareTo(a.getYear()));
                    return list;
                });
    }

    /**
     * Total revenue of a single order for a given year+month.
     * Returns exactly one row (the requested month, zeroed when the order has
     * no revenue in that month), matching the shape of the existing stats.
     */
    public Uni<List<OrderMonthTotalRevenue>> findMonthlyTotalRevenueById(String orderId, Integer year, Integer month) {
        Date start = Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", year, month)));
        int nextYear = month == 12 ? year + 1 : year;
        int nextMonth = month == 12 ? 1 : month + 1;
        Date end = Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", nextYear, nextMonth)));

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq("_id", new ObjectId(orderId)),
                Filters.eq("deletedAt", null),
                Filters.gte("createdAt", start),
                Filters.lt("createdAt", end))));
        pipeline.add(Aggregates.group(new Document("year", String.valueOf(year)).append("monthIndex", month),
                Accumulators.sum("totalRevenue", "$totalPrice")));

        return runAggregate(pipeline)
                .map(docs -> {
                    List<OrderMonthTotalRevenue> list = new ArrayList<>();
                    for (Document doc : docs) {
                        list.add(new OrderMonthTotalRevenue(
                                String.valueOf(year),
                                MONTH_NAMES[month - 1],
                                (int) ((Number) doc.getOrDefault("totalRevenue", 0)).longValue()));
                    }
                    ensureMonth(list, String.valueOf(year), MONTH_NAMES[month - 1]);
                    return list;
                });
    }

    /**
     * Total revenue of a single order for a given year.
     * Returns exactly one row (the requested year, zeroed when the order has
     * no revenue in that year).
     */
    public Uni<List<OrderYearTotalRevenue>> findYearlyTotalRevenueById(String orderId, Integer year) {
        Date start = Date.from(Instant.parse(year + "-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse((year + 1) + "-01-01T00:00:00Z"));

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq("_id", new ObjectId(orderId)),
                Filters.eq("deletedAt", null),
                Filters.gte("createdAt", start),
                Filters.lt("createdAt", end))));
        pipeline.add(Aggregates.group(new Document("year", String.valueOf(year)),
                Accumulators.sum("totalRevenue", "$totalPrice")));

        return runAggregate(pipeline)
                .map(docs -> {
                    List<OrderYearTotalRevenue> list = new ArrayList<>();
                    for (Document doc : docs) {
                        list.add(new OrderYearTotalRevenue(
                                String.valueOf(year),
                                (int) ((Number) doc.getOrDefault("totalRevenue", 0)).longValue()));
                    }
                    ensureYear(list, String.valueOf(year));
                    return list;
                });
    }
}
