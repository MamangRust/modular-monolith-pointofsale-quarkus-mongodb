package com.sanedge.transaction.repository;

import java.util.regex.Pattern;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.transaction.domain.requests.FindAllTransactionByMerchantRequest;
import com.sanedge.transaction.domain.requests.FindAllTransactionRequest;
import com.sanedge.transaction.entity.Transaction;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class TransactionQueryRepository implements ReactivePanacheMongoRepository<Transaction> {

    /**
     * Replicates the previous SQL semantics of searching the string form of
     * {@code id} / {@code paymentMethod} with a case-insensitive LIKE '%keyword%'.
     * Expects the quoted keyword as parameter {@code ?1}.
     */
    private static final String TX_SEARCH_OR = "'$or': ["
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$_id' }, 'regex': ?1, 'options': 'i' } } }, "
            + "{ 'paymentMethod': { '$regex': ?1, '$options': 'i' } }"
            + "]";

    /**
     * Merchant-scoped variant: search only {@code paymentMethod} / {@code status}.
     * Expects the quoted keyword as parameter {@code ?2} (merchantId is {@code ?1}).
     */
    private static final String MERCHANT_SEARCH_OR = "'$or': ["
            + "{ 'paymentMethod': { '$regex': ?2, '$options': 'i' } }, "
            + "{ 'status': { '$regex': ?2, '$options': 'i' } }"
            + "]";

    private int pageIndex(Integer page) {
        return page != null && page > 0 ? page - 1 : 0;
    }

    private int pageSize(Integer pageSize) {
        return pageSize != null && pageSize > 0 ? pageSize : 10;
    }

    private String keyword(String search) {
        return (search != null && !search.trim().isEmpty()) ? search.trim() : null;
    }

    private Uni<PagedResult<Transaction>> pagedResult(ReactivePanacheQuery<Transaction> panacheQuery) {
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Transaction>> findTransactions(FindAllTransactionRequest req) {
        String keyword = keyword(req.getSearch());
        Sort sort = Sort.ascending("createdAt");

        ReactivePanacheQuery<Transaction> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ }", sort);
        } else {
            panacheQuery = find("{ " + TX_SEARCH_OR + " }", sort, Pattern.quote(keyword));
        }

        return pagedResult(panacheQuery.page(pageIndex(req.getPage()), pageSize(req.getPageSize())));
    }

    public Uni<PagedResult<Transaction>> findActiveTransactions(FindAllTransactionRequest req) {
        String keyword = keyword(req.getSearch());
        Sort sort = Sort.ascending("createdAt");

        ReactivePanacheQuery<Transaction> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }", sort);
        } else {
            panacheQuery = find("{ 'deletedAt': null, " + TX_SEARCH_OR + " }", sort, Pattern.quote(keyword));
        }

        return pagedResult(panacheQuery.page(pageIndex(req.getPage()), pageSize(req.getPageSize())));
    }

    public Uni<PagedResult<Transaction>> findTrashedTransactions(FindAllTransactionRequest req) {
        String keyword = keyword(req.getSearch());
        Sort sort = Sort.descending("deletedAt");

        ReactivePanacheQuery<Transaction> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }", sort);
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, " + TX_SEARCH_OR + " }", sort,
                    Pattern.quote(keyword));
        }

        return pagedResult(panacheQuery.page(pageIndex(req.getPage()), pageSize(req.getPageSize())));
    }

    public Uni<PagedResult<Transaction>> findTransactionsByMerchant(FindAllTransactionByMerchantRequest req) {
        int pageIndex = pageIndex(req.getPage());
        int size = pageSize(req.getPageSize());
        ObjectId merchantId = req.getMerchantId() != null ? new ObjectId(req.getMerchantId()) : null;
        String keyword = keyword(req.getSearch());
        Sort sort = Sort.descending("createdAt");

        ReactivePanacheQuery<Transaction> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null, 'merchantId': ?1 }", sort, merchantId);
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'merchantId': ?1, " + MERCHANT_SEARCH_OR + " }", sort,
                    merchantId, Pattern.quote(keyword));
        }

        return pagedResult(panacheQuery.page(pageIndex, size));
    }

    public Uni<Transaction> findByTransactionId(String transactionId) {
        return find("_id", new ObjectId(transactionId)).firstResult();
    }

    public Uni<Transaction> findByOrderId(String orderId) {
        return find("{ 'orderId': ?1 }", orderId).firstResult();
    }

    /**
     * Finds the active (non-trashed) transaction carrying the given client
     * idempotency key — used to replay-safe createTransaction (Fase 12).
     */
    public Uni<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return find("{ 'deletedAt': null, 'idempotencyKey': ?1 }", idempotencyKey).firstResult();
    }
}
