package com.sanedge.category.service.impl;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.category.domain.requests.CreateCategoryRequest;
import com.sanedge.category.domain.requests.UpdateCategoryRequest;
import com.sanedge.category.domain.response.CategoryResponse;
import com.sanedge.category.domain.response.CategoryResponseDeleteAt;
import com.sanedge.category.entity.Category;
import com.sanedge.category.repository.CategoryCommandRepository;
import com.sanedge.category.repository.CategoryQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class CategoryCommandServiceImplTest {

    @Mock
    private CategoryQueryRepository categoryQueryRepo;

    @Mock
    private CategoryCommandRepository categoryCommandRepo;

    @Mock
    private Validator validator;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryCommandServiceImpl(
                categoryQueryRepo,
                categoryCommandRepo,
                validator,
                redisService,
                tracingMetrics);

        // Execute supplier directly instead of actual tracing
        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().when(validator.validate(any())).thenReturn(java.util.Collections.emptySet());
        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());
        lenient().when(categoryCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));
        lenient().when(categoryCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));
    }

    private Category createCategory(String id, String name) {
        Category cat = new Category();
        cat.setCategoryId(id);
        cat.setName(name);
        cat.setDescription("desc");
        cat.setSlugCategory("slug");
        cat.setCreatedAt(Instant.now());
        cat.setUpdatedAt(Instant.now());
        return cat;
    }

    private CreateCategoryRequest createReq(String name) {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName(name);
        req.setDescription("desc");
        req.setSlugCategory("slug");
        return req;
    }

    private UpdateCategoryRequest updateReq(String id, String name) {
        UpdateCategoryRequest req = new UpdateCategoryRequest();
        req.setCategoryId(id);
        req.setName(name);
        req.setDescription("desc");
        req.setSlugCategory("slug");
        return req;
    }

    @Nested
    @DisplayName("createCategory tests")
    class CreateCategoryTests {
        @Test
        void success() {
            CreateCategoryRequest req = createReq("New Category");
            when(categoryQueryRepo.findByName("New Category")).thenReturn(Uni.createFrom().nullItem());
            when(categoryCommandRepo.persist(any(Category.class))).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setCategoryId("507f1f77bcf86cd799439011");
                return Uni.createFrom().item(c);
            });

            ApiResponse<CategoryResponse> resp = service.createCategory(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getName()).isEqualTo("New Category");
            assertThat(resp.data().getId()).isEqualTo("507f1f77bcf86cd799439011");
        }

        @Test
        void nameAlreadyExists_returnsError() {
            CreateCategoryRequest req = createReq("Existing");
            when(categoryQueryRepo.findByName("Existing")).thenReturn(Uni.createFrom().item(createCategory("507f1f77bcf86cd799439011", "Existing")));

            ApiResponse<CategoryResponse> resp = service.createCategory(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("already exists");
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Test
        void validationFails_returnsError() {
            CreateCategoryRequest req = new CreateCategoryRequest(); // invalid
            ConstraintViolation<?> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
            when(violation.getPropertyPath()).thenReturn(org.mockito.Mockito.mock(Path.class));
            when(violation.getMessage()).thenReturn("must not be blank");
            Set violations = new HashSet();
            violations.add(violation);
            when(validator.validate(any())).thenReturn(violations);

            ApiResponse<CategoryResponse> resp = service.createCategory(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Validation failed");
        }
    }

    @Nested
    @DisplayName("updateCategory tests")
    class UpdateCategoryTests {
        @Test
        void success() {
            UpdateCategoryRequest req = updateReq("507f1f77bcf86cd799439011", "Updated Name");
            Category existing = createCategory("507f1f77bcf86cd799439011", "Old Name");
            when(categoryCommandRepo.findById(any())).thenReturn(Uni.createFrom().item(existing));
            when(categoryCommandRepo.persist(any(Category.class))).thenReturn(Uni.createFrom().item(existing));

            ApiResponse<CategoryResponse> resp = service.updateCategory(req).await().indefinitely();
            assertThat(resp.status()).describedAs(resp.message()).isEqualTo("success");
            assertThat(resp.data().getName()).isEqualTo("Updated Name");
        }

        @Test
        void categoryNotFound_returnsError() {
            UpdateCategoryRequest req = updateReq("507f1f77bcf86cd799439011", "X");
            when(categoryCommandRepo.findById(any())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<CategoryResponse> resp = service.updateCategory(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Category not found");
        }

        @Test
        void nullId_returnsError() {
            UpdateCategoryRequest req = new UpdateCategoryRequest();
            req.setCategoryId(null);
            ApiResponse<CategoryResponse> resp = service.updateCategory(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("category_id is required");
        }
    }

    @Nested
    @DisplayName("trashedCategory tests")
    class TrashedCategoryTests {
        @Test
        void success() {
            String id = "507f1f77bcf86cd799439011";
            Category trashed = createCategory("507f1f77bcf86cd799439011", "Trash");
            trashed.setDeletedAt(Instant.now());
            when(categoryCommandRepo.trashed(anyString())).thenReturn(Uni.createFrom().item(trashed));

            ApiResponse<CategoryResponseDeleteAt> resp = service.trashedCategory(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNotNull();
        }

        @Test
        void notFound_returnsError() {
            String id = "507f1f77bcf86cd799439011";
            when(categoryCommandRepo.trashed(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<CategoryResponseDeleteAt> resp = service.trashedCategory(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Category not found");
        }
    }

    @Nested
    @DisplayName("restoreCategory tests")
    class RestoreCategoryTests {
        @Test
        void success() {
            String id = "507f1f77bcf86cd799439011";
            when(categoryCommandRepo.restore(anyString())).thenReturn(Uni.createFrom().item(createCategory("507f1f77bcf86cd799439011", "Restored")));

            ApiResponse<CategoryResponseDeleteAt> resp = service.restoreCategory(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }

        @Test
        void notFoundOrNotTrashed_returnsError() {
            String id = "507f1f77bcf86cd799439011";
            when(categoryCommandRepo.restore(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<CategoryResponseDeleteAt> resp = service.restoreCategory(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Category not found or not trashed");
        }
    }

    @Nested
    @DisplayName("deleteCategoryPermanent tests")
    class DeleteCategoryPermanentTests {
        @Test
        void success() {
            String id = "507f1f77bcf86cd799439011";
            when(categoryCommandRepo.deletePermanent(anyString())).thenReturn(Uni.createFrom().item(createCategory("507f1f77bcf86cd799439011", "Del")));

            ApiResponse<Boolean> resp = service.deleteCategoryPermanent(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void notFoundOrNotTrashed_returnsError() {
            String id = "507f1f77bcf86cd799439011";
            when(categoryCommandRepo.deletePermanent(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<Boolean> resp = service.deleteCategoryPermanent(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Category not found or not trashed");
        }
    }

    @Nested
    @DisplayName("restoreAllCategories tests")
    class RestoreAllCategoriesTests {
        @Test
        void success() {
            ApiResponse<Boolean> resp = service.restoreAllCategories().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void noTrashed_throwsException() {
            when(categoryCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.restoreAllCategories().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed categories");
        }
    }

    @Nested
    @DisplayName("deleteAllCategoriesPermanent tests")
    class DeleteAllCategoriesPermanentTests {
        @Test
        void success_whenDeletedCountGreaterZero() {
            when(categoryCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));
            ApiResponse<Boolean> resp = service.deleteAllCategoriesPermanent().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void success_whenNoDeleted() {
            when(categoryCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));
            ApiResponse<Boolean> resp = service.deleteAllCategoriesPermanent().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isFalse();
        }
    }
}