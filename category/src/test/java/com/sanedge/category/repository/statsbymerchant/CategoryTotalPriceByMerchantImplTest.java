package com.sanedge.category.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceByMerchant;
import com.sanedge.category.domain.requests.FindCategoryYearTotalPriceByMerchant;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CategoryTotalPriceByMerchantRepositoryTest extends MongoTestBase {

    @Inject
    CategoryTotalPriceByMerchantRepository repository;

    @Test
    void testFindMonthlyTotalPriceByMerchant_ReturnsTwoMonthsWithZeroRevenue() {
        FindCategoryMonthTotalPriceByMerchant req = new FindCategoryMonthTotalPriceByMerchant();
        req.setMerchantId("507f1f77bcf86cd799439011");
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTotalPriceByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyTotalPriceByMerchant_ReturnsTwoYearsWithZeroRevenue() {
        FindCategoryYearTotalPriceByMerchant req = new FindCategoryYearTotalPriceByMerchant();
        req.setMerchantId("507f1f77bcf86cd799439011");
        req.setYear(2024);
        req.setYearMinusOne(2023);

        repository.findYearlyTotalPriceByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalRevenue()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }
}