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
class TransactionAmountByMerchantRepositoryTest extends MongoTestBase {

    private static final String MISSING_MERCHANT = "507f1f77bcf86cd799439099";

    @Inject
    TransactionAmountByMerchantRepository repository;

    @Test
    void testFindMonthlySuccessByMerchant_ReturnsTwoMonthsWithZero() {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(MISSING_MERCHANT);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlySuccessByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month -> {
                        assertThat(month.getTotalSuccess()).isZero();
                        assertThat(month.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlySuccessByMerchant_ReturnsTwoYearsWithZero() {
        repository.findYearlySuccessByMerchant(MISSING_MERCHANT, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getTotalSuccess()).isZero();
                        assertThat(year.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMonthlyFailedByMerchant_ReturnsTwoMonthsWithZero() {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(MISSING_MERCHANT);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyFailedByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month -> {
                        assertThat(month.getTotalFailed()).isZero();
                        assertThat(month.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyFailedByMerchant_ReturnsTwoYearsWithZero() {
        repository.findYearlyFailedByMerchant(MISSING_MERCHANT, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getTotalFailed()).isZero();
                        assertThat(year.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }
}
