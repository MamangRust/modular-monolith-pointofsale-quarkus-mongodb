package com.sanedge.transaction.service;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.requests.CreateTransactionRequest;
import com.sanedge.transaction.domain.requests.UpdateTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface TransactionCommandService {
    Uni<ApiResponse<TransactionResponse>> create(CreateTransactionRequest req);
    Uni<ApiResponse<TransactionResponse>> update(UpdateTransactionRequest req);
    Uni<ApiResponse<TransactionResponseDeleteAt>> trash(String id);
    Uni<ApiResponse<TransactionResponseDeleteAt>> restore(String id);
    Uni<ApiResponse<Boolean>> delete(String id);
    Uni<ApiResponse<Boolean>> restoreAll();
    Uni<ApiResponse<Boolean>> deleteAll();
}
