package com.sanedge.category.repository.statsbyid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceById;
import com.sanedge.category.domain.requests.FindCategoryYearTotalPriceById;
import com.sanedge.category.entity.CategoryMonthTotalPrice;
import com.sanedge.category.entity.CategoryYearTotalPrice;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

@ApplicationScoped
public class CategoryTotalPriceByIdRepository {

    private static final String[] MONTH_NAMES = { "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December" };

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
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                .append("monthIndex", new Document("$month", "$createdAt"));
        return Aggregates.group(groupId,
                Accumulators.sum("totalRevenue", "$totalPrice"));
    }

    private Bson yearGroup() {
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")));
        return Aggregates.group(groupId,
                Accumulators.sum("totalRevenue", "$totalPrice"));
    }

    private Uni<List<Document>> runAggregate(List<Bson> pipeline) {
        return Multi.createFrom().publisher(mongoClient.getDatabase(mongoDatabaseName).getCollection("orders").aggregate(pipeline))
                .collect().asList();
    }

    private Uni<List<CategoryMonthTotalPrice>> mapMonthly(List<Document> docs, Integer startYear, Integer startMonth,
            Integer endYear, Integer endMonth) {
        List<CategoryMonthTotalPrice> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new CategoryMonthTotalPrice(
                    id.getString("year"),
                    MONTH_NAMES[id.getInteger("monthIndex", 1) - 1],
                    ((Number) doc.getOrDefault("totalRevenue", 0)).longValue()));
        }
        ensureMonth(list, String.valueOf(startYear), MONTH_NAMES[startMonth - 1]);
        if (!endYear.equals(startYear) || !endMonth.equals(startMonth)) {
            ensureMonth(list, String.valueOf(endYear), MONTH_NAMES[endMonth - 1]);
        }
        list.sort((a, b) -> b.getYear().compareTo(a.getYear()));
        return Uni.createFrom().item(list);
    }

    private void ensureMonth(List<CategoryMonthTotalPrice> list, String year, String monthName) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()) && monthName.equals(r.getMonth()));
        if (!exists) {
            list.add(new CategoryMonthTotalPrice(year, monthName, 0L));
        }
    }

    private Uni<List<CategoryYearTotalPrice>> mapYearly(List<Document> docs, Integer year, Integer yearMinusOne) {
        List<CategoryYearTotalPrice> list = new ArrayList<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            list.add(new CategoryYearTotalPrice(
                    id.getString("year"),
                    ((Number) doc.getOrDefault("totalRevenue", 0)).longValue()));
        }
        ensureYear(list, String.valueOf(year));
        if (!yearMinusOne.equals(year)) {
            ensureYear(list, String.valueOf(yearMinusOne));
        }
        list.sort((a, b) -> b.getYear().compareTo(a.getYear()));
        return Uni.createFrom().item(list);
    }

    private void ensureYear(List<CategoryYearTotalPrice> list, String year) {
        boolean exists = list.stream().anyMatch(r -> year.equals(r.getYear()));
        if (!exists) {
            list.add(new CategoryYearTotalPrice(year, 0L));
        }
    }

    public Uni<List<CategoryMonthTotalPrice>> findMonthlyTotalPriceByCategoryId(FindCategoryMonthTotalPriceById req) {
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

        List<Bson> pipeline = basePipeline(start, end, null, Filters.eq("category._id", new ObjectId(req.getCategoryId())));
        pipeline.add(monthGroup());

        return runAggregate(pipeline)
                .chain(docs -> mapMonthly(docs, startYear, startMonth, endYear, endMonth));
    }

    public Uni<List<CategoryYearTotalPrice>> findYearlyTotalPriceByCategoryId(FindCategoryYearTotalPriceById req) {
        int year = req.getYear();
        int yearMinusOne = req.getYearMinusOne();
        Date start = Date.from(Instant.parse(yearMinusOne + "-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse((year + 1) + "-01-01T00:00:00Z"));

        List<Bson> pipeline = basePipeline(start, end, null, Filters.eq("category._id", new ObjectId(req.getCategoryId())));
        pipeline.add(yearGroup());

        return runAggregate(pipeline)
                .chain(docs -> mapYearly(docs, year, yearMinusOne));
    }
}
