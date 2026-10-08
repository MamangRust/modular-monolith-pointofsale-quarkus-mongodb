package com.sanedge.category.repository.statsbyid;

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
class CategoryPriceByIdRepositoryTest extends MongoTestBase {

    @Inject
    CategoryPriceByIdRepository repository;

    @Test
    void testFindMonthlyCategoryPriceById_ReturnsEmptyWhenNoData() {
        repository.findMonthlyCategoryPriceById("999999999999999999999999", 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindYearlyCategoryPriceById_ReturnsEmptyWhenNoData() {
        repository.findYearlyCategoryPriceById("999999999999999999999999", 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid().await().indefinitely();
    }
}