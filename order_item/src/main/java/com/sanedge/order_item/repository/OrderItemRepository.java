package com.sanedge.order_item.repository;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order_item.domain.requests.FindAllOrderItems;
import com.sanedge.order_item.entity.OrderItem;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OrderItemRepository implements ReactivePanacheMongoRepository<OrderItem> {

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

    /**
     * Builds the paginated search query. deletedFilter: "active" → only live
     * documents, "trashed" → only soft-deleted ones, null → everything.
     * Replicates the previous SQL semantics of searching the string form of
     * id / orderId / productId with a LIKE '%keyword%'.
     */
    private ReactivePanacheQuery<OrderItem> pagedSearch(FindAllOrderItems req, String deletedFilter, Sort sort) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim()
                : null;

        ReactivePanacheQuery<OrderItem> panacheQuery;
        if (keyword == null) {
            if ("active".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': null }", sort);
            } else if ("trashed".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': { '$ne': null } }", sort);
            } else {
                panacheQuery = findAll(sort);
            }
        } else {
            String quoted = Pattern.quote(keyword);
            if ("active".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': null, " + ID_SEARCH_OR + " }", sort, quoted);
            } else if ("trashed".equals(deletedFilter)) {
                panacheQuery = find("{ 'deletedAt': { '$ne': null }, " + ID_SEARCH_OR + " }", sort, quoted);
            } else {
                panacheQuery = find("{ " + ID_SEARCH_OR + " }", sort, quoted);
            }
        }
        return panacheQuery.page(pageIndex, req.getPageSize());
    }

    public Uni<PagedResult<OrderItem>> findOrderItems(FindAllOrderItems req) {
        var panacheQuery = pagedSearch(req, null, Sort.ascending("createdAt"));

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<OrderItem>> findActiveOrderItems(FindAllOrderItems req) {
        var panacheQuery = pagedSearch(req, "active", Sort.ascending("createdAt"));

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<OrderItem>> findTrashedOrderItems(FindAllOrderItems req) {
        var panacheQuery = pagedSearch(req, "trashed", Sort.descending("deletedAt"));

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }
}
