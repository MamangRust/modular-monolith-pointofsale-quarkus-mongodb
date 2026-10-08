package com.sanedge.cashier.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesById;
import com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesById;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CashierTotalSalesByIdRepositoryTest extends MongoTestBase {

    @Inject
    CashierTotalSalesByIdRepository repository;

    @Test
    void testFindMonthTotalSalesById_ReturnsTwoMonthsWithZeroSales() {
        FindCashierMonthTotalSalesById req = new FindCashierMonthTotalSalesById();
        req.setCashierId("507f1f77bcf86cd799439011");
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthTotalSalesById(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalSales()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearTotalSalesById_ReturnsTwoYearsWithZeroSales() {
        FindCashierYearTotalSalesById req = new FindCashierYearTotalSalesById();
        req.setCashierId("507f1f77bcf86cd799439011");
        req.setYear(2024);
        req.setYearMinusOne(2023);

        repository.findYearTotalSalesById(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalSales()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }
}