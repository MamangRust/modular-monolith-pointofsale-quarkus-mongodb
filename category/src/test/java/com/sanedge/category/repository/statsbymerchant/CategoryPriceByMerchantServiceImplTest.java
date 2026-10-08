package com.sanedge.category.repository.statsbymerchant;

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
class CategoryPriceByMerchantRepositoryTest extends MongoTestBase {

    @Inject
    CategoryPriceByMerchantRepository repository;

    @Test
    void testFindMonthlyCategoryPriceByMerchant_ReturnsEmptyWhenNoData() {
        repository.findMonthlyCategoryPriceByMerchant("507f1f77bcf86cd799439011", 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyCategoryPriceByMerchant_ReturnsEmptyWhenNoData() {
        repository.findYearlyCategoryPriceByMerchant("507f1f77bcf86cd799439011", 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }
}