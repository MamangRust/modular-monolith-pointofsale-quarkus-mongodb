package com.sanedge.category.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceRange;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CategoryTotalPriceRepositoryTest extends MongoTestBase {

    @Inject
    CategoryTotalPriceRepository repository;

    @Test
    void testFindMonthlyTotalPrice_ReturnsTwoMonthsWithZeroRevenue() {
        FindCategoryMonthTotalPriceRange req = new FindCategoryMonthTotalPriceRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTotalPrice(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isZero());
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyTotalPrice_ReturnsTwoYearsWithZeroRevenue() {
        repository.findYearlyTotalPrice(2024, 2023)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalRevenue()).isZero();
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }
}