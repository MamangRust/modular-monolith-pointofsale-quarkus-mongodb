package com.sanedge.transaction.repository.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.sanedge.common.enums.PaymentStatus;
import com.sanedge.transaction.domain.requests.FindTransactionMonthRange;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;
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
public class TransactionAmountStatusRepository {

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

    private Uni<List<Document>> monthlyAggregate(int year, int month, int prevYear, int prevMonth,
            PaymentStatus status) {
        Date start1 = Date.from(Instant.parse(firstDayOfMonth(year, month)));
        Date end1 = Date.from(Instant.parse(firstDayOfNextMonth(year, month)));
        Date start2 = Date.from(Instant.parse(firstDayOfMonth(prevYear, prevMonth)));
        Date end2 = Date.from(Instant.parse(firstDayOfNextMonth(prevYear, prevMonth)));

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
                        .append("monthIndex", new Document("$month", "$createdAt")),
                Accumulators.sum("totalCount", 1),
                Accumulators.sum("totalAmount", "$amount")));
        return runAggregate(pipeline);
    }

    private Uni<List<Document>> yearlyAggregate(int year, PaymentStatus status) {
        Date start = Date.from(Instant.parse(firstDayOfMonth(year - 1, 1)));
        Date end = Date.from(Instant.parse(firstDayOfMonth(year + 1, 1)));

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq("deletedAt", null),
                Filters.eq("status", status.name()),
                Filters.gte("createdAt", start),
                Filters.lt("createdAt", end))));
        pipeline.add(Aggregates.group(
                new Document("year",
                        new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt"))),
                Accumulators.sum("totalCount", 1),
                Accumulators.sum("totalAmount", "$amount")));
        return runAggregate(pipeline);
    }

    private void ensureMonthSuccess(List<TransactionMonthlyAmountSuccess> list, String year, int month) {
        String abbr = StatsSupport.monthAbbr(month);
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()) && abbr.equals(r.getMonth()));
        if (!exists) {
            list.add(new TransactionMonthlyAmountSuccess(year, abbr, 0, 0L));
        }
    }

    private void ensureMonthFailed(List<TransactionMonthlyAmountFailed> list, String year, int month) {
        String abbr = StatsSupport.monthAbbr(month);
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()) && abbr.equals(r.getMonth()));
        if (!exists) {
            list.add(new TransactionMonthlyAmountFailed(year, abbr, 0, 0L));
        }
    }

    private void ensureYearSuccess(List<TransactionYearlyAmountSuccess> list, String year) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()));
        if (!exists) {
            list.add(new TransactionYearlyAmountSuccess(year, 0, 0L));
        }
    }

    private void ensureYearFailed(List<TransactionYearlyAmountFailed> list, String year) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()));
        if (!exists) {
            list.add(new TransactionYearlyAmountFailed(year, 0, 0L));
        }
    }

    private void sortMonthlyByYearThenMonthDesc(List<?> list) {
        list.sort((a, b) -> {
            String yearA = monthYear(a);
            String yearB = monthYear(b);
            int byYear = yearB.compareTo(yearA);
            if (byYear != 0) {
                return byYear;
            }
            return Integer.compare(StatsSupport.monthIndex(monthName(b)), StatsSupport.monthIndex(monthName(a)));
        });
    }

    private String monthYear(Object row) {
        if (row instanceof TransactionMonthlyAmountSuccess r) {
            return r.getYear();
        }
        return ((TransactionMonthlyAmountFailed) row).getYear();
    }

    private String monthName(Object row) {
        if (row instanceof TransactionMonthlyAmountSuccess r) {
            return r.getMonth();
        }
        return ((TransactionMonthlyAmountFailed) row).getMonth();
    }

    public Uni<List<TransactionMonthlyAmountSuccess>> findMonthlyTransactionSuccess(FindTransactionMonthRange req) {
        int year = req.getStartYear();
        int month = req.getStartMonth();
        int prevYear = req.getEndYear();
        int prevMonth = req.getEndMonth();

        return monthlyAggregate(year, month, prevYear, prevMonth, PaymentStatus.SUCCESS)
                .map(docs -> {
                    List<TransactionMonthlyAmountSuccess> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new TransactionMonthlyAmountSuccess(
                                id.getString("year"),
                                StatsSupport.monthAbbr(id.getInteger("monthIndex")),
                                ((Number) doc.getOrDefault("totalCount", 0)).intValue(),
                                ((Number) doc.getOrDefault("totalAmount", 0)).longValue()));
                    }
                    ensureMonthSuccess(list, String.valueOf(year), month);
                    ensureMonthSuccess(list, String.valueOf(prevYear), prevMonth);
                    sortMonthlyByYearThenMonthDesc(list);
                    return list;
                });
    }

    public Uni<List<TransactionYearlyAmountSuccess>> findYearlyTransactionSuccess(Integer year) {
        return yearlyAggregate(year, PaymentStatus.SUCCESS)
                .map(docs -> {
                    List<TransactionYearlyAmountSuccess> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new TransactionYearlyAmountSuccess(
                                id.getString("year"),
                                ((Number) doc.getOrDefault("totalCount", 0)).intValue(),
                                ((Number) doc.getOrDefault("totalAmount", 0)).longValue()));
                    }
                    ensureYearSuccess(list, String.valueOf(year));
                    ensureYearSuccess(list, String.valueOf(year - 1));
                    list.sort((a, b) -> b.getYear().compareTo(a.getYear()));
                    return list;
                });
    }

    public Uni<List<TransactionMonthlyAmountFailed>> findMonthlyTransactionFailed(FindTransactionMonthRange req) {
        int year = req.getStartYear();
        int month = req.getStartMonth();
        int prevYear = req.getEndYear();
        int prevMonth = req.getEndMonth();

        return monthlyAggregate(year, month, prevYear, prevMonth, PaymentStatus.FAILED)
                .map(docs -> {
                    List<TransactionMonthlyAmountFailed> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new TransactionMonthlyAmountFailed(
                                id.getString("year"),
                                StatsSupport.monthAbbr(id.getInteger("monthIndex")),
                                ((Number) doc.getOrDefault("totalCount", 0)).intValue(),
                                ((Number) doc.getOrDefault("totalAmount", 0)).longValue()));
                    }
                    ensureMonthFailed(list, String.valueOf(year), month);
                    ensureMonthFailed(list, String.valueOf(prevYear), prevMonth);
                    sortMonthlyByYearThenMonthDesc(list);
                    return list;
                });
    }

    public Uni<List<TransactionYearlyAmountFailed>> findYearlyTransactionFailed(Integer year) {
        return yearlyAggregate(year, PaymentStatus.FAILED)
                .map(docs -> {
                    List<TransactionYearlyAmountFailed> list = new ArrayList<>();
                    for (Document doc : docs) {
                        Document id = doc.get("_id", Document.class);
                        list.add(new TransactionYearlyAmountFailed(
                                id.getString("year"),
                                ((Number) doc.getOrDefault("totalCount", 0)).intValue(),
                                ((Number) doc.getOrDefault("totalAmount", 0)).longValue()));
                    }
                    ensureYearFailed(list, String.valueOf(year));
                    ensureYearFailed(list, String.valueOf(year - 1));
                    list.sort((a, b) -> b.getYear().compareTo(a.getYear()));
                    return list;
                });
    }
}
