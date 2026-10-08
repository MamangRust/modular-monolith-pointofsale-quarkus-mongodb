package com.sanedge.cashier.repository.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.sanedge.cashier.domain.requests.FindCashierMonthSales;
import com.sanedge.cashier.entity.CashierMonthSales;
import com.sanedge.cashier.entity.CashierYearSales;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
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
public class CashierSalesRepository {

    private static final String[] MONTH_NAMES = { "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December" };

    @Inject
    ReactiveMongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "POINT_OF_SALE")
    String mongoDatabaseName;

    private List<Bson> basePipeline(Date start, Date end, Bson orderFilter, Bson cashierFilter) {
        List<Bson> matchFilters = new ArrayList<>();
        matchFilters.add(Filters.eq("deletedAt", null));
        matchFilters.add(Filters.gte("createdAt", start));
        matchFilters.add(Filters.lt("createdAt", end));
        if (orderFilter != null) {
            matchFilters.add(orderFilter);
        }

        List<Bson> stages = new ArrayList<>();
        stages.add(Aggregates.match(Filters.and(matchFilters)));
        stages.add(Aggregates.lookup("cashiers", "cashierId", "_id", "cashier"));
        stages.add(Aggregates.unwind("$cashier"));
        stages.add(Aggregates.match(cashierFilter != null
                ? Filters.and(Filters.eq("cashier.deletedAt", null), cashierFilter)
                : Filters.eq("cashier.deletedAt", null)));
        return stages;
    }

    private Bson monthGroup() {
        Document groupId = new Document("month",
                new Document("$dateToString", new Document("format", "%b").append("date", "$createdAt")))
                .append("monthIndex", new Document("$month", "$createdAt"))
                .append("cashierId", "$cashier._id")
                .append("cashierName", "$cashier.name");
        return Aggregates.group(groupId,
                Accumulators.sum("orderCount", 1),
                Accumulators.sum("totalSales", "$totalPrice"));
    }

    private Bson yearGroup() {
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                .append("cashierId", "$cashier._id")
                .append("cashierName", "$cashier.name");
        return Aggregates.group(groupId,
                Accumulators.sum("orderCount", 1),
                Accumulators.sum("totalSales", "$totalPrice"));
    }

    private Uni<List<Document>> runAggregate(List<Bson> pipeline) {
        return Multi.createFrom().publisher(mongoClient.getDatabase(mongoDatabaseName).getCollection("orders").aggregate(pipeline))
                .collect().asList();
    }

    private Uni<List<CashierMonthSales>> mapMonthly(List<Document> docs) {
        List<CashierMonthSales> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new CashierMonthSales(
                    id.getString("month"),
                    id.get("cashierId", ObjectId.class) != null ? id.get("cashierId", ObjectId.class).toHexString() : null,
                    id.getString("cashierName"),
                    doc.getInteger("orderCount", 0),
                    ((Number) doc.getOrDefault("totalSales", 0)).longValue()));
        }
        list.sort((a, b) -> Integer.compare(monthIndexOf(a.getMonth()), monthIndexOf(b.getMonth())));
        return Uni.createFrom().item(list);
    }

    private int monthIndexOf(String monthName) {
        for (int i = 0; i < MONTH_NAMES.length; i++) {
            if (MONTH_NAMES[i].equalsIgnoreCase(monthName)) {
                return i;
            }
        }
        return 0;
    }

    private Uni<List<CashierYearSales>> mapYearly(List<Document> docs) {
        List<CashierYearSales> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new CashierYearSales(
                    id.getString("year"),
                    id.get("cashierId", ObjectId.class) != null ? id.get("cashierId", ObjectId.class).toHexString() : null,
                    id.getString("cashierName"),
                    doc.getInteger("orderCount", 0),
                    ((Number) doc.getOrDefault("totalSales", 0)).longValue()));
        }
        list.sort((a, b) -> a.getYear().compareTo(b.getYear()));
        return Uni.createFrom().item(list);
    }

    public Uni<List<CashierMonthSales>> findMonthSales(FindCashierMonthSales req) {
        int year = req.getYear();
        int startMonth = req.getStartMonth();
        int endMonth = req.getEndMonth();
        Date start = Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", year, startMonth)));
        int endNextYear = endMonth == 12 ? year + 1 : year;
        int endNextMonth = endMonth == 12 ? 1 : endMonth + 1;
        Date end = Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", endNextYear, endNextMonth)));

        List<Bson> pipeline = basePipeline(start, end, null, null);
        pipeline.add(monthGroup());
        pipeline.add(new Document("$sort", new Document("_id.monthIndex", 1)));

        return runAggregate(pipeline).chain(docs -> mapMonthly(docs));
    }

    public Uni<List<CashierYearSales>> findYearSales(Integer year) {
        Date start = Date.from(Instant.parse((year - 4) + "-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse((year + 1) + "-01-01T00:00:00Z"));

        List<Bson> pipeline = basePipeline(start, end, null, null);
        pipeline.add(yearGroup());
        pipeline.add(new Document("$sort", new Document("_id.year", 1)));

        return runAggregate(pipeline).chain(docs -> mapYearly(docs));
    }
}
