package com.sanedge.order.repository;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import com.sanedge.order.entity.OrderItem;
import com.sanedge.common.domain.response.PagedResult;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OrderItemRepository implements ReactivePanacheMongoRepository<OrderItem> {

    /**
     * Replicates the previous SQL semantics of searching the string form of
     * id / orderId / productId with a LIKE '%keyword%'.
     */
    private static final String ID_SEARCH_OR = "'$or': ["
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$_id' }, 'regex': ?1 } } }, "
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$orderId' }, 'regex': ?1 } } }, "
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$productId' }, 'regex': ?1 } } }"
            + "]";

    public Uni<List<OrderItem>> findOrderItemByOrder(String orderId) {
        return find("{ 'orderId': ?1, 'deletedAt': null }", new ObjectId(orderId)).list();
    }

    public Uni<List<OrderItem>> findOrderItemByOrderIncludingDeleted(String orderId) {
        return find("orderId", new ObjectId(orderId)).list();
    }

    public Uni<OrderItem> trashed(String orderItemId) {
        return find("_id", new ObjectId(orderItemId)).firstResult()
                .chain(item -> {
                    if (item != null && item.getDeletedAt() == null) {
                        item.setDeletedAt(Instant.now());
                        return item.update().map(v -> item);
                    }
                    return Uni.createFrom().item(item);
                });
    }

    public Uni<OrderItem> restore(String orderItemId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderItemId)).firstResult()
                .chain(item -> {
                    if (item != null) {
                        item.setDeletedAt(null);
                        return item.update().map(v -> item);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<OrderItem> deletePermanent(String orderItemId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderItemId)).firstResult()
                .chain(item -> {
                    if (item != null) {
                        return item.delete().map(v -> item);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Boolean> restoreAllDeleted() {
        return update("{ '$set': { 'deletedAt': null } }")
                .where("{ 'deletedAt': { '$ne': null } }")
                .map(updatedCount -> updatedCount > 0);
    }

    public Uni<Boolean> deleteAllDeleted() {
        return delete("{ 'deletedAt': { '$ne': null } }")
                .map(deletedCount -> deletedCount > 0);
    }

    private ReactivePanacheQuery<OrderItem> pagedSearch(String keyword, String deletedFilter, Sort sort) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        ReactivePanacheQuery<OrderItem> panacheQuery;
        if (searchKeyword == null) {
            if ("active".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': null }", sort);
            } else if ("trashed".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': { '$ne': null } }", sort);
            } else {
                panacheQuery = findAll(sort);
            }
        } else {
            String quoted = Pattern.quote(searchKeyword);
            if ("active".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': null, " + ID_SEARCH_OR + " }", sort, quoted);
            } else if ("trashed".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': { '$ne': null }, " + ID_SEARCH_OR + " }", sort, quoted);
            } else {
                panacheQuery = find("{ " + ID_SEARCH_OR + " }", sort, quoted);
            }
        }
        return panacheQuery;
    }

    public Uni<PagedResult<OrderItem>> findOrderItems(String keyword, int page, int size) {
        int pageIndex = page > 0 ? page - 1 : 0;
        var panacheQuery = pagedSearch(keyword, null, Sort.ascending("createdAt")).page(pageIndex, size);
        return pagedResult(panacheQuery);
    }

    public Uni<PagedResult<OrderItem>> findActiveOrderItems(String keyword, int page, int size) {
        int pageIndex = page > 0 ? page - 1 : 0;
        var panacheQuery = pagedSearch(keyword, "active", Sort.ascending("createdAt")).page(pageIndex, size);
        return pagedResult(panacheQuery);
    }

    public Uni<PagedResult<OrderItem>> findTrashedOrderItems(String keyword, int page, int size) {
        int pageIndex = page > 0 ? page - 1 : 0;
        var panacheQuery = pagedSearch(keyword, "trashed", Sort.descending("deletedAt")).page(pageIndex, size);
        return pagedResult(panacheQuery);
    }

    private Uni<PagedResult<OrderItem>> pagedResult(ReactivePanacheQuery<OrderItem> panacheQuery) {
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }
}
