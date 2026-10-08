package com.sanedge.cashier.repository.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.sanedge.cashier.domain.requests.FindMonthTotalSalesRange;
import com.sanedge.cashier.entity.CashierMonthTotalSales;
import com.sanedge.cashier.entity.CashierYearTotalSales;
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
public class CashierTotalSalesRepository {

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
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                .append("monthIndex", new Document("$month", "$createdAt"));
        return Aggregates.group(groupId,
                Accumulators.sum("totalSales", "$totalPrice"));
    }

    private Bson yearGroup() {
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")));
        return Aggregates.group(groupId,
                Accumulators.sum("totalSales", "$totalPrice"));
    }

    private Uni<List<Document>> runAggregate(List<Bson> pipeline) {
        return Multi.createFrom().publisher(mongoClient.getDatabase(mongoDatabaseName).getCollection("orders").aggregate(pipeline))
                .collect().asList();
    }

    private Uni<List<CashierMonthTotalSales>> mapMonthly(List<Document> docs, Integer startYear, Integer startMonth,
            Integer endYear, Integer endMonth) {
        List<CashierMonthTotalSales> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new CashierMonthTotalSales(id.getString("year"),
                    MONTH_NAMES[id.getInteger("monthIndex", 1) - 1],
                    ((Number) doc.getOrDefault("totalSales", 0)).longValue()));
        }
        ensureMonth(list, String.valueOf(startYear), MONTH_NAMES[startMonth - 1]);
        if (!endYear.equals(startYear) || !endMonth.equals(startMonth)) {
            ensureMonth(list, String.valueOf(endYear), MONTH_NAMES[endMonth - 1]);
        }
        list.sort((a, b) -> b.getYear().compareTo(a.getYear()));
        return Uni.createFrom().item(list);
    }

    private void ensureMonth(List<CashierMonthTotalSales> list, String year, String monthName) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()) && monthName.equals(r.getMonth()));
        if (!exists) {
            list.add(new CashierMonthTotalSales(year, monthName, 0L));
        }
    }

    private Uni<List<CashierYearTotalSales>> mapYearly(List<Document> docs, Integer year, Integer yearMinusOne) {
        List<CashierYearTotalSales> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new CashierYearTotalSales(id.getString("year"),
                    ((Number) doc.getOrDefault("totalSales", 0)).longValue()));
        }
        ensureYear(list, String.valueOf(year));
        if (!yearMinusOne.equals(year)) {
            ensureYear(list, String.valueOf(yearMinusOne));
        }
        list.sort((a, b) -> b.getYear().compareTo(a.getYear()));
        return Uni.createFrom().item(list);
    }

    private void ensureYear(List<CashierYearTotalSales> list, String year) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()));
        if (!exists) {
            list.add(new CashierYearTotalSales(year, 0L));
        }
    }

    public Uni<List<CashierMonthTotalSales>> findMonthTotalSales(FindMonthTotalSalesRange req) {
        int startYear = req.getStartYear();
        int startMonth = req.getStartMonth();
        int endYear = req.getEndYear();
        int endMonth = req.getEndMonth();
        Date start = Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", startYear, startMonth)));
        int endNextYear = endMonth == 12 ? endYear + 1 : endYear;
        int endNextMonth = endMonth == 12 ? 1 : endMonth + 1;
        Date end = Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", endNextYear, endNextMonth)));

        List<Bson> pipeline = basePipeline(start, end, null, null);
        pipeline.add(monthGroup());
        pipeline.add(new Document("$sort", new Document("_id.monthIndex", 1)));

        return runAggregate(pipeline).chain(docs -> mapMonthly(docs, startYear, startMonth, endYear, endMonth));
    }

    public Uni<List<CashierYearTotalSales>> findYearTotalSales(Integer year, Integer yearMinusOne) {
        Date start = Date.from(Instant.parse(yearMinusOne + "-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse((year + 1) + "-01-01T00:00:00Z"));

        List<Bson> pipeline = basePipeline(start, end, null, null);
        pipeline.add(yearGroup());
        pipeline.add(new Document("$sort", new Document("_id.year", -1)));

        return runAggregate(pipeline).chain(docs -> mapYearly(docs, year, yearMinusOne));
    }
}
