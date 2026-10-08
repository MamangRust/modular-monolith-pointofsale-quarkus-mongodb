package com.sanedge.cashier.repository;

import java.util.List;
import java.util.regex.Pattern;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.cashier.entity.Cashier;
import com.sanedge.cashier.domain.requests.FindAllCashierMerchant;
import com.sanedge.cashier.domain.requests.FindAllCashiers;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class CashierQueryRepository implements ReactivePanacheMongoRepository<Cashier> {

    public Uni<PagedResult<Cashier>> findAllCashiers(FindAllCashiers req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Cashier> panacheQuery;
        if (keyword == null) {
            panacheQuery = findAll(Sort.ascending("createdAt"));
        } else {
            panacheQuery = find("{ 'name': { '$regex': ?1, '$options': 'i' } }", Sort.ascending("createdAt"),
                    Pattern.quote(keyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Cashier>> findActiveCashiers(FindAllCashiers req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Cashier> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }", Sort.ascending("createdAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Sort.ascending("createdAt"), Pattern.quote(keyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Cashier>> findTrashedCashiers(FindAllCashiers req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Cashier> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }", Sort.descending("deletedAt"));
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Sort.descending("deletedAt"), Pattern.quote(keyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Cashier>> findByMerchants(FindAllCashierMerchant req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;
        String merchantId = req.getMerchantId();

        ReactivePanacheQuery<Cashier> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null, 'merchantId': ?1 }", Sort.ascending("createdAt"),
                    new ObjectId(merchantId));
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'merchantId': ?1, 'name': { '$regex': ?2, '$options': 'i' } }",
                    Sort.ascending("createdAt"), new ObjectId(merchantId), Pattern.quote(keyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Cashier> findByCashierId(String cashierId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(cashierId)).firstResult();
    }

    public Uni<Cashier> findByNameAndMerchantId(String name, String merchantId) {
        return find("{ 'name': ?1, 'merchantId': ?2 }", name, new ObjectId(merchantId)).firstResult();
    }

    public Uni<List<Cashier>> findByMerchantId(String merchantId) {
        return find("{ 'deletedAt': null, 'merchantId': ?1 }", new ObjectId(merchantId)).list();
    }
}
