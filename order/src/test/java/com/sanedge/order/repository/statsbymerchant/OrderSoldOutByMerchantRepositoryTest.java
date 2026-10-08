package com.sanedge.order.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderSoldOutByMerchantRepositoryTest extends MongoTestBase {

    private static final String MISSING_MERCHANT = "507f1f77bcf86cd799439099";

    @Inject
    OrderSoldOutByMerchantRepository repository;

    @Test
    void testFindMonthlyOrdersByMerchant_ReturnsEmptyWhenNoData() {
        repository.findMonthlyOrdersByMerchant(MISSING_MERCHANT, 202401)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyOrdersByMerchant_ReturnsEmptyWhenNoData() {
        repository.findYearlyOrdersByMerchant(MISSING_MERCHANT, 202401)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }
}
