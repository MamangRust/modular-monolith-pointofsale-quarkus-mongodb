package com.sanedge.category.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceById;
import com.sanedge.category.domain.requests.FindCategoryYearTotalPriceById;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CategoryTotalPriceByIdRepositoryTest extends MongoTestBase {

    @Inject
    CategoryTotalPriceByIdRepository repository;

    @Test
    void testFindMonthlyTotalPriceByCategoryId_ReturnsTwoMonthsWithZeroRevenue() {
        FindCategoryMonthTotalPriceById req = new FindCategoryMonthTotalPriceById();
        req.setCategoryId("507f1f77bcf86cd799439011");
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTotalPriceByCategoryId(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyTotalPriceByCategoryId_ReturnsTwoYearsWithZeroRevenue() {
        FindCategoryYearTotalPriceById req = new FindCategoryYearTotalPriceById();
        req.setCategoryId("507f1f77bcf86cd799439011");
        req.setYear(2024);
        req.setYearMinusOne(2023);

        repository.findYearlyTotalPriceByCategoryId(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalRevenue()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }
}