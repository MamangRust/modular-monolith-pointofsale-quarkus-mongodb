package com.sanedge.order.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.order.domain.requests.FindOrderMonthRange;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderTotalRevenueRepositoryTest extends MongoTestBase {

    @Inject
    OrderTotalRevenueRepository repository;

    @Test
    void testFindMonthlyTotalRevenue_ReturnsTwoMonthsWithZeroRevenue() {
        FindOrderMonthRange req = new FindOrderMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTotalRevenue(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isEqualTo(0));
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyTotalRevenue_ReturnsTwoYearsWithZeroRevenue() {
        repository.findYearlyTotalRevenue(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalRevenue()).isEqualTo(0);
                    });
                })
                .replaceWithVoid().await().indefinitely();
    }
}
