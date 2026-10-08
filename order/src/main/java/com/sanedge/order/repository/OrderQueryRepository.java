package com.sanedge.order.repository;

import java.util.regex.Pattern;

import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order.entity.Order;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OrderQueryRepository implements ReactivePanacheMongoRepository<Order> {

    /**
     * Replicates the previous SQL semantics of searching the string form of
     * id / cashierId with a LIKE '%keyword%'.
     */
    private static final String ID_SEARCH_OR = "'$or': ["
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$_id' }, 'regex': ?1 } } }, "
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$cashierId' }, 'regex': ?1 } } }"
            + "]";

    private ReactivePanacheQuery<Order> pagedSearch(FindAllOrderRequest req, BsonFilter filter, Sort sort) {
        int pageIndex = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim()
                : null;

        ReactivePanacheQuery<Order> panacheQuery;
        if (keyword == null) {
            panacheQuery = find(filter.json(), sort);
        } else {
            String quoted = Pattern.quote(keyword);
            panacheQuery = find(filter.jsonWithSearch(), sort, quoted);
        }
        return panacheQuery.page(pageIndex, size);
    }

    private Uni<PagedResult<Order>> pagedResult(ReactivePanacheQuery<Order> panacheQuery) {
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findOrders(FindAllOrderRequest req) {
        return pagedResult(pagedSearch(req, new BsonFilter() {
            @Override
            public String json() {
                return "{ }";
            }

            @Override
            public String jsonWithSearch() {
                return "{ " + ID_SEARCH_OR + " }";
            }
        }, Sort.ascending("createdAt")));
    }

    public Uni<PagedResult<Order>> findActiveOrders(FindAllOrderRequest req) {
        return pagedResult(pagedSearch(req, new BsonFilter() {
            @Override
            public String json() {
                return "{ 'deletedAt': null }";
            }

            @Override
            public String jsonWithSearch() {
                return "{ 'deletedAt': null, " + ID_SEARCH_OR + " }";
            }
        }, Sort.ascending("createdAt")));
    }

    public Uni<PagedResult<Order>> findTrashedOrders(FindAllOrderRequest req) {
        return pagedResult(pagedSearch(req, new BsonFilter() {
            @Override
            public String json() {
                return "{ 'deletedAt': { '$ne': null } }";
            }

            @Override
            public String jsonWithSearch() {
                return "{ 'deletedAt': { '$ne': null }, " + ID_SEARCH_OR + " }";
            }
        }, Sort.descending("deletedAt")));
    }

    public Uni<PagedResult<Order>> findOrdersByMerchant(FindAllOrderByMerchantRequest req) {
        int pageIndex = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        ObjectId merchantId = req.getMerchantId() != null ? new ObjectId(req.getMerchantId()) : null;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim()
                : null;

        ReactivePanacheQuery<Order> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'merchantId': ?1 }", Sort.ascending("createdAt"), merchantId);
        } else {
            panacheQuery = find("{ 'merchantId': ?2, " + ID_SEARCH_OR + " }", Sort.ascending("createdAt"),
                    Pattern.quote(keyword), merchantId);
        }

        return pagedResult(panacheQuery.page(pageIndex, size));
    }

    public Uni<Order> findOrderById(String orderId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(orderId)).firstResult();
    }

    private interface BsonFilter {
        String json();

        String jsonWithSearch();
    }
}
