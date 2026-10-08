package com.sanedge.cashier.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthSalesById;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CashierSalesByIdRepositoryTest extends MongoTestBase {

    @Inject
    CashierSalesByIdRepository repository;

    @Test
    void testFindMonthSalesById_ReturnsEmptyWhenNoData() {
        FindCashierMonthSalesById req = new FindCashierMonthSalesById();
        req.setCashierId("507f1f77bcf86cd799439011");
        req.setYear(2024);
        req.setStartMonth(1);
        req.setEndMonth(6);

        repository.findMonthSalesById(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearSalesById_ReturnsEmptyWhenNoData() {
        repository.findYearSalesById("507f1f77bcf86cd799439012", 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }
}