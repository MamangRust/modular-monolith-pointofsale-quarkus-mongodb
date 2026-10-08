package com.sanedge.cashier.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesByMerchant;
import com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesByMerchant;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CashierTotalSalesByMerchantRepositoryTest extends MongoTestBase {

    @Inject
    CashierTotalSalesByMerchantRepository repository;

    @Test
    void testFindMonthTotalSalesByMerchant_ReturnsTwoMonthsWithZeroSales() {
        FindCashierMonthTotalSalesByMerchant req = new FindCashierMonthTotalSalesByMerchant();
        req.setMerchantId("507f1f77bcf86cd799439011");
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthTotalSalesByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalSales()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearTotalSalesByMerchant_ReturnsTwoYearsWithZeroSales() {
        FindCashierYearTotalSalesByMerchant req = new FindCashierYearTotalSalesByMerchant();
        req.setMerchantId("507f1f77bcf86cd799439011");
        req.setYear(2024);
        req.setYearMinusOne(2023);

        repository.findYearTotalSalesByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalSales()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }
}