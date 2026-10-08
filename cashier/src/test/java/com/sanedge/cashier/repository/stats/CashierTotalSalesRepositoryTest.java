package com.sanedge.cashier.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindMonthTotalSalesRange;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CashierTotalSalesRepositoryTest extends MongoTestBase {

    @Inject
    CashierTotalSalesRepository repository;

    @Test
    void testFindMonthTotalSales_ReturnsTwoMonthsWithZeroSales() {
        FindMonthTotalSalesRange req = new FindMonthTotalSalesRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthTotalSales(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month -> {
                        assertThat(month.getMonth()).isNotNull();
                        assertThat(month.getTotalSales()).isZero();
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearTotalSales_ReturnsTwoYearsWithZeroSales() {
        repository.findYearTotalSales(2024, 2023)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalSales()).isZero();
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }
}