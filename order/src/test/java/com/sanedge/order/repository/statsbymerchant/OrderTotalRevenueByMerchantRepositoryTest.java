package com.sanedge.order.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.order.domain.requests.FindOrderMonthMerchantRange;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderTotalRevenueByMerchantRepositoryTest extends MongoTestBase {

    private static final String MISSING_MERCHANT = "507f1f77bcf86cd799439099";

    @Inject
    OrderTotalRevenueByMerchantRepository repository;

    @Test
    void testFindMonthlyTotalRevenueByMerchant_ReturnsTwoMonthsWithZeroRevenue() {
        FindOrderMonthMerchantRange req = new FindOrderMonthMerchantRange();
        req.setMerchantId(MISSING_MERCHANT);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        repository.findMonthlyTotalRevenueByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isEqualTo(0));
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyTotalRevenueByMerchant_ReturnsTwoYearsWithZeroRevenue() {
        repository.findYearlyTotalRevenueByMerchant(MISSING_MERCHANT, 2024)
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
