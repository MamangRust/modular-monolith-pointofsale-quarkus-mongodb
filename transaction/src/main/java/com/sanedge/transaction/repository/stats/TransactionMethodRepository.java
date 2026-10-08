package com.sanedge.transaction.repository.stats;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.sanedge.common.enums.PaymentStatus;
import com.sanedge.transaction.domain.requests.FindTransactionMonthRange;
import com.sanedge.transaction.entity.TransactionMonthlyMethod;
import com.sanedge.transaction.entity.TransactionYearMethod;
import com.sanedge.transaction.repository.StatsSupport;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TransactionMethodRepository {

    private static final String COLLECTION = "transactions";

    @Inject
    ReactiveMongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "POINT_OF_SALE")
    String mongoDatabaseName;

    private Uni<List<Document>> runAggregate(List<Bson> pipeline) {
        return Multi.createFrom()
                .publisher(mongoClient.getDatabase(mongoDatabaseName).getCollection(COLLECTION).aggregate(pipeline))
                .collect().asList();
    }

    private static String firstDayOfMonth(int year, int month) {
        return String.format("%04d-%02d-01T00:00:00Z", year, month);
    }

    private static String firstDayOfNextMonth(int year, int month) {
        int nextYear = month == 12 ? year + 1 : year;
        int nextMonth = month == 12 ? 1 : month + 1;
        return firstDayOfMonth(nextYear, nextMonth);
    }

    /**
     * Distinct payment methods across all non-trashed transactions — mirrors the
     * SQL {@code payment_methods} CTE which ignores status and date range.
     */
    private Uni<List<String>> distinctPaymentMethods() {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.eq("deletedAt", null)));
        pipeline.add(Aggregates.group("$paymentMethod"));
        pipeline.add(new Document("$sort", new Document("_id", 1)));
        return runAggregate(pipeline)
                .map(docs -> {
                    List<String> list = new ArrayList<>();
                    for (Document doc : docs) {
                        String method = doc.getString("_id");
                        if (method != null) {
                            list.add(method);
                        }
                    }
                    return list;
                });
    }

    private Uni<List<Document>> monthlyTransactions(Date start1, Date end1, Date start2, Date end2,
            PaymentStatus status) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq("deletedAt", null),
                Filters.eq("status", status.name()),
                Filters.or(
                        Filters.and(Filters.gte("createdAt", start1), Filters.lt("createdAt", end1)),
                        Filters.and(Filters.gte("createdAt", start2), Filters.lt("createdAt", end2))))));
        pipeline.add(Aggregates.group(
                new Document("year",
                        new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                        .append("monthIndex", new Document("$month", "$createdAt"))
                        .append("paymentMethod", "$paymentMethod"),
                Accumulators.sum("totalTransactions", 1),
                Accumulators.sum("totalAmount", "$amount")));
        return runAggregate(pipeline);
    }

    private Uni<List<Document>> yearlyTransactions(int startYear, int endYear, PaymentStatus status) {
        Date start = Date.from(Instant.parse(firstDayOfMonth(startYear, 1)));
        Date end = Date.from(Instant.parse(firstDayOfMonth(endYear + 1, 1)));

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq("deletedAt", null),
                Filters.eq("status", status.name()),
                Filters.gte("createdAt", start),
                Filters.lt("createdAt", end))));
        pipeline.add(Aggregates.group(
                new Document("year",
                        new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                        .append("paymentMethod", "$paymentMethod"),
                Accumulators.sum("totalTransactions", 1),
                Accumulators.sum("totalAmount", "$amount")));
        return runAggregate(pipeline);
    }

    private static String key(String year, int monthIndex, String method) {
        return year + "|" + monthIndex + "|" + method;
    }

    private List<TransactionMonthlyMethod> buildMonthly(List<String> methods, List<Document> docs,
            YearMonth from, YearMonth to) {
        Map<String, Document> index = new HashMap<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            index.put(key(id.getString("year"), id.getInteger("monthIndex"), id.getString("paymentMethod")), doc);
        }

        List<TransactionMonthlyMethod> list = new ArrayList<>();
        YearMonth cursor = from;
        while (!cursor.isAfter(to)) {
            String year = String.valueOf(cursor.getYear());
            int month = cursor.getMonthValue();
            for (String method : methods) {
                Document doc = index.get(key(year, month, method));
                int total = doc != null ? ((Number) doc.getOrDefault("totalTransactions", 0)).intValue() : 0;
                long amount = doc != null ? ((Number) doc.getOrDefault("totalAmount", 0)).longValue() : 0L;
                list.add(new TransactionMonthlyMethod(StatsSupport.monthAbbr(month), method, total, amount));
            }
            cursor = cursor.plusMonths(1);
        }
        return list;
    }

    private List<TransactionYearMethod> buildYearly(List<String> methods, List<Document> docs,
            int startYear, int endYear) {
        Map<String, Document> index = new HashMap<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            index.put(id.getString("year") + "|" + id.getString("paymentMethod"), doc);
        }

        List<TransactionYearMethod> list = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            String yearKey = String.valueOf(year);
            for (String method : methods) {
                Document doc = index.get(yearKey + "|" + method);
                int total = doc != null ? ((Number) doc.getOrDefault("totalTransactions", 0)).intValue() : 0;
                long amount = doc != null ? ((Number) doc.getOrDefault("totalAmount", 0)).longValue() : 0L;
                list.add(new TransactionYearMethod(yearKey, method, total, amount));
            }
        }
        return list;
    }

    private Uni<List<TransactionMonthlyMethod>> monthlyMethods(int year1, int month1, int year2, int month2,
            PaymentStatus status) {
        YearMonth a = YearMonth.of(year1, month1);
        YearMonth b = YearMonth.of(year2, month2);
        YearMonth from = a.isBefore(b) ? a : b;
        YearMonth to = a.isAfter(b) ? a : b;

        Date start1 = Date.from(Instant.parse(firstDayOfMonth(year1, month1)));
        Date end1 = Date.from(Instant.parse(firstDayOfNextMonth(year1, month1)));
        Date start2 = Date.from(Instant.parse(firstDayOfMonth(year2, month2)));
        Date end2 = Date.from(Instant.parse(firstDayOfNextMonth(year2, month2)));

        return Uni.combine().all()
                .unis(distinctPaymentMethods(), monthlyTransactions(start1, end1, start2, end2, status))
                .asTuple()
                .map(tuple -> buildMonthly(tuple.getItem1(), tuple.getItem2(), from, to));
    }

    private Uni<List<TransactionYearMethod>> yearlyMethods(int year, PaymentStatus status) {
        int startYear = year - 1;
        int endYear = year;
        return Uni.combine().all()
                .unis(distinctPaymentMethods(), yearlyTransactions(startYear, endYear, status))
                .asTuple()
                .map(tuple -> buildYearly(tuple.getItem1(), tuple.getItem2(), startYear, endYear));
    }

    public Uni<List<TransactionMonthlyMethod>> findMonthlyMethodsSuccess(FindTransactionMonthRange req) {
        return monthlyMethods(req.getStartYear(), req.getStartMonth(), req.getEndYear(), req.getEndMonth(),
                PaymentStatus.SUCCESS);
    }

    public Uni<List<TransactionMonthlyMethod>> findMonthlyMethodsFailed(FindTransactionMonthRange req) {
        return monthlyMethods(req.getStartYear(), req.getStartMonth(), req.getEndYear(), req.getEndMonth(),
                PaymentStatus.FAILED);
    }

    public Uni<List<TransactionYearMethod>> findYearlyMethodsSuccess(Integer year) {
        return yearlyMethods(year, PaymentStatus.SUCCESS);
    }

    public Uni<List<TransactionYearMethod>> findYearlyMethodsFailed(Integer year) {
        return yearlyMethods(year, PaymentStatus.FAILED);
    }
}
