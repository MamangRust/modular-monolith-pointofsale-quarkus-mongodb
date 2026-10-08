package com.sanedge.product.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.mongodb.client.model.Filters;
import com.sanedge.product.domain.requests.FindAllProductByMerchantRequest;
import com.sanedge.product.domain.requests.FindAllProductByCategoryRequest;
import com.sanedge.product.domain.requests.FindAllProductRequest;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.product.entity.Product;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ProductQueryRepository implements ReactivePanacheMongoRepository<Product> {

    @Inject
    ReactiveMongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "POINT_OF_SALE")
    String mongoDatabaseName;

    public Uni<PagedResult<Product>> findAllProducts(FindAllProductRequest req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Product> panacheQuery;
        if (searchKeyword == null) {
            panacheQuery = findAll(Sort.ascending("createdAt"));
        } else {
            panacheQuery = find("{ 'name': { '$regex': ?1, '$options': 'i' } }", Sort.ascending("createdAt"),
                    Pattern.quote(searchKeyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findActiveProducts(FindAllProductRequest req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Product> panacheQuery;
        if (searchKeyword == null) {
            panacheQuery = find("{ 'deletedAt': null }", Sort.ascending("createdAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Sort.ascending("createdAt"), Pattern.quote(searchKeyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findTrashedProducts(FindAllProductRequest req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Product> panacheQuery;
        if (searchKeyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }", Sort.descending("deletedAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Sort.descending("deletedAt"), Pattern.quote(searchKeyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Product> findProductById(String productId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(productId)).firstResult();
    }

    public Uni<PagedResult<Product>> findProductsByMerchant(FindAllProductByMerchantRequest req) {
        int pageIndex = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String merchantId = req.getMerchantId();
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;
        String categoryId = (req.getCategoryId() != null && !req.getCategoryId().isEmpty()) ? req.getCategoryId() : null;
        Integer minPrice = req.getMinPrice();
        Integer maxPrice = req.getMaxPrice();

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        conditions.add("'deletedAt': null");
        if (merchantId != null && !merchantId.isEmpty()) {
            conditions.add("'merchantId': ?1");
            params.add(new ObjectId(merchantId));
        }
        if (searchKeyword != null) {
            conditions.add("'name': { '$regex': ?" + (params.size() + 1) + ", '$options': 'i' }");
            params.add(Pattern.quote(searchKeyword));
        }
        if (categoryId != null) {
            conditions.add("'categoryId': ?" + (params.size() + 1));
            params.add(new ObjectId(categoryId));
        }
        if (minPrice != null && maxPrice != null) {
            conditions.add("'price': { '$gte': ?" + (params.size() + 1) + ", '$lte': ?" + (params.size() + 2) + " }");
            params.add(minPrice);
            params.add(maxPrice);
        } else if (minPrice != null) {
            conditions.add("'price': { '$gte': ?" + (params.size() + 1) + " }");
            params.add(minPrice);
        } else if (maxPrice != null) {
            conditions.add("'price': { '$lte': ?" + (params.size() + 1) + " }");
            params.add(maxPrice);
        }

        String query = "{ " + String.join(", ", conditions) + " }";
        ReactivePanacheQuery<Product> panacheQuery = find(query, Sort.ascending("createdAt"), params.toArray());
        panacheQuery.page(pageIndex, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findProductsByCategory(FindAllProductByCategoryRequest req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        // Resolve category ids by (case-insensitive) category name, then filter products by them.
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        conditions.add("{ 'deletedAt': null, 'categoryId': { '$in': ?1 } }");
        params.add(new ArrayList<>()); // placeholder replaced below via lookup

        return findCategoryIdsByName(req.getCategoryName()).chain(categoryIds -> {
            if (categoryIds.isEmpty()) {
                return Uni.createFrom().item(new PagedResult<>(new ArrayList<Product>(), 0));
            }
            List<Object> filterParams = new ArrayList<>();
            filterParams.add(categoryIds);
            String query;
            if (searchKeyword != null) {
                query = "{ 'deletedAt': null, 'categoryId': { '$in': ?1 }, 'name': { '$regex': ?2, '$options': 'i' } }";
                filterParams.add(Pattern.quote(searchKeyword));
            } else {
                query = "{ 'deletedAt': null, 'categoryId': { '$in': ?1 } }";
            }
            if (req.getMinPrice() != null) {
                query = query.substring(0, query.length() - 2) + ", 'price': { '$gte': ?" + (filterParams.size() + 1) + " } }";
                filterParams.add(req.getMinPrice());
            }
            if (req.getMaxPrice() != null) {
                query = query.substring(0, query.length() - 2) + ", 'price': { '$lte': ?" + (filterParams.size() + 1) + " } }";
                filterParams.add(req.getMaxPrice());
            }

            ReactivePanacheQuery<Product> panacheQuery = find(query, Sort.ascending("createdAt"), filterParams.toArray());
            panacheQuery.page(pageIndex, req.getPageSize());

            return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                    .asTuple()
                    .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
        });
    }

    private Uni<List<ObjectId>> findCategoryIdsByName(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return Uni.createFrom().item(new ArrayList<>());
        }
        return Multi.createFrom()
                .publisher(mongoClient.getDatabase(mongoDatabaseName).getCollection("categories")
                        .find(Filters.regex("name", Pattern.quote(categoryName.trim()), "i")))
                .collect().asList()
                .map(docs -> {
                    List<ObjectId> ids = new ArrayList<>();
                    for (Document doc : docs) {
                        ids.add(doc.getObjectId("_id"));
                    }
                    return ids;
                });
    }
}
