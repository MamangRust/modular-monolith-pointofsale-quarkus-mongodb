package com.sanedge.transaction.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.transaction.domain.requests.FindTransactionMonthMerchantRange;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class TransactionMethodByMerchantRepositoryTest extends MongoTestBase {

    private static final String MISSING_MERCHANT = "507f1f77bcf86cd799439099";

    @Inject
    TransactionMethodByMerchantRepository repository;

    @Test
    void testFindMonthlyMethodsSuccessByMerchant_ReturnsEmptyWhenNoData() {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(MISSING_MERCHANT);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTransactionMethodsSuccessByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMonthlyMethodsFailedByMerchant_ReturnsEmptyWhenNoData() {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(MISSING_MERCHANT);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTransactionMethodsFailedByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyMethodsSuccessByMerchant_ReturnsEmptyWhenNoData() {
        repository.findYearlyTransactionMethodsSuccessByMerchant(MISSING_MERCHANT, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyMethodsFailedByMerchant_ReturnsEmptyWhenNoData() {
        repository.findYearlyTransactionMethodsFailedByMerchant(MISSING_MERCHANT, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }
}
