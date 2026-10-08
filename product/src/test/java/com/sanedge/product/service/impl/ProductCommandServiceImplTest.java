package com.sanedge.product.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.product.domain.requests.CreateProductRequest;
import com.sanedge.product.domain.requests.UpdateProductRequest;
import com.sanedge.product.domain.response.ProductResponse;
import com.sanedge.product.domain.response.ProductResponseDeleteAt;
import com.sanedge.product.entity.Product;
import com.sanedge.product.repository.ProductCommandRepository;
import com.sanedge.product.repository.ProductQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceImplTest {

    @Mock
    private ProductCommandRepository productCommandRepo;
    @Mock
    private ProductQueryRepository productQueryRepo;
    @Mock
    private Validator validator;
    @Mock
    private RedisService redisService;
    @Mock
    private TracingMetrics tracingMetrics;
    @Mock
    private com.sanedge.common.adapter.merchant.MerchantQueryPort merchantQueryPort;
    @Mock
    private com.sanedge.common.adapter.category.CategoryQueryPort categoryQueryPort;

    private ProductCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductCommandServiceImpl(productCommandRepo, productQueryRepo, validator, redisService,
                tracingMetrics, merchantQueryPort, categoryQueryPort);

        // Lenient stub for traceAndMeasure
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());
        lenient().when(productCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));
        lenient().when(productCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

        // common merchant lookup via port
        lenient().when(merchantQueryPort.findByMerchantId(anyString()))
                .thenReturn(Uni.createFrom().item(new com.sanedge.common.adapter.model.Merchant(
                        "507f1f77bcf86cd799439011", "Test Merchant", "api-key", "SUCCESS",
                        "507f1f77bcf86cd799439013", Instant.now(), Instant.now())));

        // common category lookup via port
        lenient().when(categoryQueryPort.findByCategoryId(anyString()))
                .thenReturn(Uni.createFrom().item(new com.sanedge.common.adapter.model.Category(
                        "507f1f77bcf86cd799439011", "Test Category", "desc", "slug", "img",
                        Instant.now(), Instant.now())));
    }

    private Product createMockProduct(String id) {
        Product p = new Product();
        p.setProductId(id);
        p.id = id != null ? new ObjectId(id) : null;
        p.setName("Test Product");
        p.setMerchantId(new ObjectId("507f1f77bcf86cd799439011"));
        p.setCategoryId(new ObjectId("507f1f77bcf86cd799439011"));
        p.setPrice(5000);
        p.setCountInStock(20);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }

    private CreateProductRequest createReq() {
        CreateProductRequest r = new CreateProductRequest();
        r.setMerchantId("507f1f77bcf86cd799439011");
        r.setCategoryId("507f1f77bcf86cd799439011");
        r.setName("New Product");
        r.setDescription("desc");
        r.setPrice(5000);
        r.setCountInStock(10);
        r.setBrand("Brand");
        r.setWeight(500);
        r.setSlugProduct("new-product");
        r.setImageProduct("img.png");
        return r;
    }

    private UpdateProductRequest updateReq() {
        UpdateProductRequest r = new UpdateProductRequest();
        r.setProductId("507f1f77bcf86cd799439011");
        r.setMerchantId("507f1f77bcf86cd799439011");
        r.setCategoryId("507f1f77bcf86cd799439011");
        r.setName("Updated");
        r.setDescription("desc2");
        r.setPrice(6000);
        r.setCountInStock(15);
        r.setBrand("Brand2");
        r.setWeight(600);
        r.setSlugProduct("updated-product");
        r.setImageProduct("img2.png");
        return r;
    }

    @Nested
    @DisplayName("createProduct tests")
    class CreateProductTests {
        @Test
        void success() {
            CreateProductRequest req = createReq();
            Product saved = createMockProduct("507f1f77bcf86cd799439011");
            saved.setName(req.getName());
            when(productCommandRepo.persist(any(Product.class))).thenReturn(Uni.createFrom().item(saved));

            ApiResponse<ProductResponse> resp = service.createProduct(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getId()).isEqualTo("507f1f77bcf86cd799439011");
            assertThat(resp.data().getName()).isEqualTo("New Product");
        }

        @Test
        void persistenceFailure_returnsError() {
            CreateProductRequest req = createReq();
            when(productCommandRepo.persist(any(Product.class)))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));
            ApiResponse<ProductResponse> resp = service.createProduct(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Failed to create product");
        }
    }

    @Nested
    @DisplayName("updateProduct tests")
    class UpdateProductTests {
        @Test
        void success() {
            UpdateProductRequest req = updateReq();
            Product saved = createMockProduct("507f1f77bcf86cd799439011");
            saved.setName(req.getName());
            saved.setPrice(req.getPrice());
            saved.setCountInStock(req.getCountInStock());
            when(productQueryRepo.findProductById(anyString())).thenReturn(Uni.createFrom().item(createMockProduct("507f1f77bcf86cd799439011")));
            when(productCommandRepo.persist(any(Product.class))).thenReturn(Uni.createFrom().item(saved));

            ApiResponse<ProductResponse> resp = service.updateProduct(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getName()).isEqualTo("Updated");
        }

        @Test
        void productNotFound_returnsError() {
            UpdateProductRequest req = updateReq();
            when(productQueryRepo.findProductById(anyString())).thenReturn(Uni.createFrom().nullItem());

            ApiResponse<ProductResponse> resp = service.updateProduct(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Product not found");
        }

        @Test
        void merchantNotFound_returnsError() {
            lenient().when(merchantQueryPort.findByMerchantId(anyString()))
                    .thenReturn(Uni.createFrom().failure(new com.sanedge.common.exception.ResourceNotFoundException(
                            "Merchant not found: 507f1f77bcf86cd799439011")));

            UpdateProductRequest req = updateReq();

            ApiResponse<ProductResponse> resp = service.updateProduct(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Merchant not found");
        }
    }

    @Nested
    @DisplayName("trashedProduct tests")
    class TrashedProductTests {
        @Test
        void success() {
            String id = "507f1f77bcf86cd799439011";
            Product trashed = createMockProduct("507f1f77bcf86cd799439011");
            trashed.setDeletedAt(Instant.now());
            when(productCommandRepo.trashed(anyString())).thenReturn(Uni.createFrom().item(trashed));

            ApiResponse<ProductResponseDeleteAt> resp = service.trashedProduct(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNotNull();
        }

        @Test
        void notFound_returnsError() {
            when(productCommandRepo.trashed(anyString())).thenReturn(Uni.createFrom().nullItem());
            ApiResponse<ProductResponseDeleteAt> resp = service.trashedProduct("507f1f77bcf86cd799439011").await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Failed to trash product");
        }
    }

    @Nested
    @DisplayName("restoreProduct tests")
    class RestoreProductTests {
        @Test
        void success() {
            String id = "507f1f77bcf86cd799439011";
            when(productCommandRepo.restore(anyString())).thenReturn(Uni.createFrom().item(createMockProduct("507f1f77bcf86cd799439011")));
            ApiResponse<ProductResponseDeleteAt> resp = service.restoreProduct(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data().getDeletedAt()).isNull();
        }

        @Test
        void notFound_returnsError() {
            when(productCommandRepo.restore(anyString())).thenReturn(Uni.createFrom().nullItem());
            ApiResponse<ProductResponseDeleteAt> resp = service.restoreProduct("507f1f77bcf86cd799439011").await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
            assertThat(resp.message()).contains("Failed to restore product");
        }
    }

    @Nested
    @DisplayName("deleteProductPermanent tests")
    class DeleteProductPermanentTests {
        @Test
        void success() {
            String id = "507f1f77bcf86cd799439011";

            when(productCommandRepo.findById(any()))
                    .thenReturn(Uni.createFrom().item(createMockProduct("507f1f77bcf86cd799439011")));

            ApiResponse<Boolean> resp = service.deleteProductPermanent(id).await().indefinitely();

            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isFalse();
        }

        @Test
        void notFound_returnsFalse() {
            String id = "507f1f77bcf86cd799439011";
            
            when(productCommandRepo.findById(any()))
                    .thenReturn(Uni.createFrom().item((Product) null));

            ApiResponse<Boolean> resp = service.deleteProductPermanent(id).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isFalse();
        }
    }

    @Nested
    @DisplayName("restoreAllProducts tests")
    class RestoreAllProductsTests {
        @Test
        void success() {
            ApiResponse<Boolean> resp = service.restoreAllProducts().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void noTrashed_throwsException() {
            when(productCommandRepo.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.restoreAllProducts().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No products found in trash");
        }
    }

    @Nested
    @DisplayName("deleteAllProductsPermanent tests")
    class DeleteAllProductsPermanentTests {
        @Test
        void success() {
            ApiResponse<Boolean> resp = service.deleteAllProductsPermanent().await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).isTrue();
        }

        @Test
        void noTrashed_throwsException() {
            when(productCommandRepo.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));
            assertThatThrownBy(() -> service.deleteAllProductsPermanent().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No products found in trash");
        }
    }
}