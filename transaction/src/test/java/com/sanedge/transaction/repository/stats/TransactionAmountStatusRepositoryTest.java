package com.sanedge.transaction.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.transaction.domain.requests.FindTransactionMonthRange;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class TransactionAmountStatusRepositoryTest extends MongoTestBase {

    @Inject
    TransactionAmountStatusRepository repository;

    @Test
    void testFindMonthlyTransactionSuccess_ReturnsTwoMonthsWithZero() {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTransactionSuccess(req)
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
    void testFindYearlyTransactionSuccess_ReturnsTwoYearsWithZero() {
        repository.findYearlyTransactionSuccess(2024)
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
    void testFindMonthlyTransactionFailed_ReturnsTwoMonthsWithZero() {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTransactionFailed(req)
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
    void testFindYearlyTransactionFailed_ReturnsTwoYearsWithZero() {
        repository.findYearlyTransactionFailed(2024)
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
