package com.sanedge.cashier.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthSales;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CashierSalesRepositoryTest extends MongoTestBase {

    @Inject
    CashierSalesRepository repository;

    @Test
    void testFindMonthSales_ReturnsEmptyWhenNoData() {
        FindCashierMonthSales req = new FindCashierMonthSales();
        req.setYear(2024);
        req.setStartMonth(1);
        req.setEndMonth(6);

        repository.findMonthSales(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearSales_ReturnsEmptyWhenNoData() {
        repository.findYearSales(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }
}