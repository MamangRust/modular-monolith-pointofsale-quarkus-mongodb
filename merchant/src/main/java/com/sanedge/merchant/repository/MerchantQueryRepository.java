package com.sanedge.merchant.repository;

import java.util.List;
import java.util.regex.Pattern;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.merchant.domain.requests.FindAllMerchants;
import com.sanedge.merchant.entity.Merchant;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class MerchantQueryRepository implements ReactivePanacheMongoRepository<Merchant> {

    private static final String SEARCH_OR = "{ '$or': ["
            + " { 'name': { '$regex': ?1, '$options': 'i' } },"
            + " { 'apiKey': { '$regex': ?1, '$options': 'i' } },"
            + " { 'status': { '$regex': ?1, '$options': 'i' } } ] }";

    public Uni<Boolean> existsByName(String name) {
        return count("{ 'name': { '$regex': ?1, '$options': 'i' }, 'deletedAt': null }",
                "^" + Pattern.quote(name) + "$").map(c -> c > 0);
    }

    public Uni<PagedResult<Merchant>> findMerchants(FindAllMerchants req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Merchant> panacheQuery;
        if (searchKeyword == null) {
            panacheQuery = findAll(Sort.ascending("createdAt"));
        } else {
            panacheQuery = find(SEARCH_OR, Sort.ascending("createdAt"), Pattern.quote(searchKeyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Merchant>> findActiveMerchants(FindAllMerchants req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Merchant> panacheQuery;
        if (searchKeyword == null) {
            panacheQuery = find("{ 'deletedAt': null }", Sort.ascending("createdAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': null, '$or': ["
                    + " { 'name': { '$regex': ?1, '$options': 'i' } },"
                    + " { 'apiKey': { '$regex': ?1, '$options': 'i' } },"
                    + " { 'status': { '$regex': ?1, '$options': 'i' } } ] }",
                    Sort.ascending("createdAt"), Pattern.quote(searchKeyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Merchant>> findTrashedMerchants(FindAllMerchants req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String searchKeyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Merchant> panacheQuery;
        if (searchKeyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }", Sort.descending("deletedAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, '$or': ["
                    + " { 'name': { '$regex': ?1, '$options': 'i' } },"
                    + " { 'apiKey': { '$regex': ?1, '$options': 'i' } },"
                    + " { 'status': { '$regex': ?1, '$options': 'i' } } ] }",
                    Sort.descending("deletedAt"), Pattern.quote(searchKeyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Merchant> findMerchantById(String merchantId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(merchantId)).firstResult();
    }

    public Uni<Merchant> findByApiKey(String apiKey) {
        return find("{ 'apiKey': ?1, 'deletedAt': null }", apiKey).firstResult();
    }

    public Uni<Merchant> findByName(String name) {
        return find("{ 'name': ?1, 'deletedAt': null }", name).firstResult();
    }

    public Uni<List<Merchant>> findByUserId(String userId) {
        if (userId == null) {
            return Uni.createFrom().item(List.of());
        }
        return list("{ 'userId': ?1, 'deletedAt': null }", new ObjectId(userId));
    }
}
