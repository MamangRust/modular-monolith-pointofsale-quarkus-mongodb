package com.sanedge.category.repository;

import java.util.List;
import java.util.regex.Pattern;

import com.sanedge.category.domain.requests.FindAllCategory;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.category.entity.Category;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class CategoryQueryRepository implements ReactivePanacheMongoRepository<Category> {

    public Uni<PagedResult<Category>> findCategories(FindAllCategory req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Category> panacheQuery;
        if (keyword == null) {
            panacheQuery = findAll(Sort.ascending("createdAt"));
        } else {
            panacheQuery = find("{ 'name': { '$regex': ?1, '$options': 'i' } }", Sort.ascending("createdAt"),
                    Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Category>> findActiveCategories(FindAllCategory req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Category> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }", Sort.ascending("createdAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Sort.ascending("createdAt"), Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Category>> findTrashedCategories(FindAllCategory req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Category> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }", Sort.descending("deletedAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Sort.descending("deletedAt"), Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<List<Category>> findNameAndId() {
        return find("{ 'deletedAt': null }", Sort.ascending("name")).list();
    }

    public Uni<Category> findByName(String name) {
        return find("name", name).firstResult();
    }

    public Uni<Category> findCategoryById(String categoryId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(categoryId)).firstResult();
    }
}
