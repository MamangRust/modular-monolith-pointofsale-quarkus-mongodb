package com.sanedge.order_item.service.impl;

import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bson.types.ObjectId;
import com.sanedge.common.adapter.model.UpdateProductCmd;
import com.sanedge.common.adapter.order.OrderCommandPort;
import com.sanedge.common.adapter.order.OrderQueryPort;
import com.sanedge.common.adapter.product.ProductCommandPort;
import com.sanedge.common.adapter.product.ProductQueryPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.repository.OrderItemRepository;
import com.sanedge.order_item.service.OrderItemCommandService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class OrderItemCommandServiceImpl implements OrderItemCommandService {
    private static final Logger logger = LoggerFactory.getLogger(OrderItemCommandServiceImpl.class);

    private final OrderItemRepository orderItemRepository;
    private final Validator validator;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final OrderQueryPort orderQueryPort;
    private final OrderCommandPort orderCommandPort;
    private final ProductQueryPort productQueryPort;
    private final ProductCommandPort productCommandPort;

    @Inject
    public OrderItemCommandServiceImpl(OrderItemRepository orderItemRepository,
            Validator validator,
            RedisService redisService,
            TracingMetrics tracingMetrics,
            OrderQueryPort orderQueryPort,
            OrderCommandPort orderCommandPort,
            ProductQueryPort productQueryPort,
            ProductCommandPort productCommandPort) {
        this.orderItemRepository = orderItemRepository;
        this.validator = validator;
        this.redisService = redisService;
        this.tracingMetrics = tracingMetrics;
        this.orderQueryPort = orderQueryPort;
        this.orderCommandPort = orderCommandPort;
        this.productQueryPort = productQueryPort;
        this.productCommandPort = productCommandPort;
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    private Uni<Void> updateOrderTotalPrice(String orderId) {
        return orderItemRepository.findOrderItemByOrder(orderId)
                .chain(items -> {
                    long total = items.stream().mapToLong(item -> item.getPrice().longValue() * item.getQuantity())
                            .sum();
                    return orderCommandPort
                            .updateOrderTotalPrice(orderId, (int) total)
                            .invoke(v -> logger.info("Order total price updated successfully for orderId={}",
                                    orderId));
                });
    }

    private Uni<Void> clearCache(String orderId) {
        if (orderId == null) {
            return Uni.createFrom().voidItem();
        }
        String cacheKey = "order_item:by_order:" + orderId;
        return redisService.deleteReactive(cacheKey);
    }

    @Override
    public Uni<ApiResponse<OrderItemResponse>> create(CreateOrderItemRequest request) {
        logger.info("Creating new order item for orderId={} and productId={}", request.getOrderId(),
                request.getProductId());
        Attributes attrs = Attributes.builder()
                .put("order.id", request.getOrderId() != null ? request.getOrderId() : "null")
                .put("product.id", request.getProductId() != null ? request.getProductId() : "null")
                .build();

        return runTraced("createOrderItem", "create_order_item", attrs,
                () -> {
                    try {
                        validateRequest(request);
                    } catch (Exception e) {
                        return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
                    }

                    return orderQueryPort.findById(request.getOrderId())
                            .chain(order -> productQueryPort.findById(request.getProductId()))
                            .chain(product -> {
                                if (product.countInStock() < request.getQuantity()) {
                                    logger.error("Insufficient stock for product id={}", request.getProductId());
                                    throw new IllegalArgumentException(
                                            "Insufficient stock for product id=" + request.getProductId());
                                }

                                OrderItem orderItem = new OrderItem();
                                orderItem.setOrderId(new ObjectId(request.getOrderId()));
                                orderItem.setProductId(new ObjectId(request.getProductId()));
                                orderItem.setQuantity(request.getQuantity());
                                orderItem.setPrice(request.getPrice());
                                Instant now = Instant.now();
                                orderItem.setCreatedAt(now);
                                orderItem.setUpdatedAt(now);

                                UpdateProductCmd updateCmd = new UpdateProductCmd(
                                        product.id(),
                                        product.merchantId(),
                                        product.categoryId(),
                                        product.name(),
                                        product.description(),
                                        product.price(),
                                        product.countInStock() - request.getQuantity(),
                                        product.brand(),
                                        product.weight(),
                                        product.imageProduct());

                                return Uni.combine().all().unis(
                                        orderItemRepository.persist(orderItem),
                                        productCommandPort.update(updateCmd)).asTuple().map(t -> t.getItem1());
                            })
                            .chain(savedItem -> updateOrderTotalPrice(request.getOrderId())
                                    .chain(() -> clearCache(request.getOrderId()))
                                    .map(v -> savedItem))
                            .map(savedItem -> {
                                logger.info("Order item created successfully with id={}", savedItem.getOrderItemId());
                                return ApiResponse.success("Order item created successfully",
                                        OrderItemResponse.from(savedItem));
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("Failed to create order item", e);
                                return new ApiResponse<>("error", "Failed to create your order item: " + e.getMessage(),
                                        null);
                            });
                });
    }

    @Override
    public Uni<ApiResponse<OrderItemResponse>> update(UpdateOrderItemRequest request) {
        logger.info("Updating order item id={}", request.getOrderItemId());
        Attributes attrs = Attributes.builder()
                .put("order_item.id", request.getOrderItemId() != null ? request.getOrderItemId() : "null")
                .build();

        return runTraced("updateOrderItem", "update_order_item", attrs,
                () -> {
                    try {
                        validateRequest(request);
                    } catch (Exception e) {
                        return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
                    }

                    return orderItemRepository.findById(new ObjectId(request.getOrderItemId()))
                            .chain(existingItem -> {
                                if (existingItem == null) {
                                    logger.error("Order item not found with id={}", request.getOrderItemId());
                                    throw new ResourceNotFoundException("Order item not found");
                                }

                                return productQueryPort.findById(request.getProductId())
                                        .chain(product -> {
                                            int diff = request.getQuantity() - existingItem.getQuantity();
                                            if (diff > 0 && product.countInStock() < diff) {
                                                logger.error("Insufficient stock for product id={}",
                                                        request.getProductId());
                                                throw new IllegalArgumentException(
                                                        "Insufficient stock for product id=" + request.getProductId());
                                            }

                                            existingItem.setOrderId(new ObjectId(request.getOrderId()));
                                            existingItem.setProductId(new ObjectId(request.getProductId()));
                                            existingItem.setQuantity(request.getQuantity());
                                            existingItem.setPrice(request.getPrice());
                                            existingItem.setUpdatedAt(Instant.now());

                                            UpdateProductCmd updateCmd = new UpdateProductCmd(
                                                    product.id(),
                                                    product.merchantId(),
                                                    product.categoryId(),
                                                    product.name(),
                                                    product.description(),
                                                    product.price(),
                                                    product.countInStock() - diff,
                                                    product.brand(),
                                                    product.weight(),
                                                    product.imageProduct());

                                            return Uni.combine().all().unis(
                                                    orderItemRepository.persist(existingItem),
                                                    productCommandPort.update(updateCmd)).asTuple()
                                                    .map(t -> t.getItem1());
                                        });
                            })
                            .chain(savedItem -> updateOrderTotalPrice(request.getOrderId())
                                    .chain(() -> clearCache(request.getOrderId()))
                                    .map(v -> savedItem))
                            .map(savedItem -> {
                                logger.info("Order item updated successfully with id={}", savedItem.getOrderItemId());
                                return ApiResponse.success("Order item updated successfully",
                                        OrderItemResponse.from(savedItem));
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("Failed to update order item id={}", request.getOrderItemId(), e);
                                return new ApiResponse<>("error", "Failed to update order item: " + e.getMessage(),
                                        null);
                            });
                });
    }

    @Override
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> trash(String id) {
        logger.info("Trashing order item id={}", id);
        Attributes attrs = Attributes.builder()
                .put("order_item.id", id)
                .build();

        return runTraced("trashOrderItem", "trash_order_item", attrs,
                () -> orderItemRepository.trashed(id)
                        .chain(item -> {
                            if (item == null) {
                                logger.error("Order item not found for trashing id={}", id);
                                throw new ResourceNotFoundException("Order item not found");
                            }
                            return updateOrderTotalPrice(item.getOrderId().toHexString())
                                    .chain(() -> clearCache(item.getOrderId().toHexString()))
                                    .map(v -> item);
                        })
                        .map(item -> {
                            logger.info("Order item trashed successfully with id={}", id);
                            return ApiResponse.success("Order item trashed successfully",
                                    OrderItemResponseDeleteAt.from(item));
                        })
                        .onFailure().recoverWithItem(e -> {
                            logger.error("Failed to trash order item id={}", id, e);
                            return new ApiResponse<>("error", "Failed to trash order item: " + e.getMessage(), null);
                        }));
    }

    @Override
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> restore(String id) {
        logger.info("Restoring order item id={}", id);
        Attributes attrs = Attributes.builder()
                .put("order_item.id", id)
                .build();

        return runTraced("restoreOrderItem", "restore_order_item", attrs,
                () -> orderItemRepository.restore(id)
                        .chain(item -> {
                            if (item == null) {
                                logger.error("Order item not found for restoration id={}", id);
                                throw new ResourceNotFoundException("Order item not found or not in trash");
                            }
                            return updateOrderTotalPrice(item.getOrderId().toHexString())
                                    .chain(() -> clearCache(item.getOrderId().toHexString()))
                                    .map(v -> item);
                        })
                        .map(item -> {
                            logger.info("Order item restored successfully with id={}", id);
                            return ApiResponse.success("Order item restored successfully",
                                    OrderItemResponseDeleteAt.from(item));
                        })
                        .onFailure().recoverWithItem(e -> {
                            logger.error("Failed to restore order item id={}", id, e);
                            return new ApiResponse<>("error", "Failed to restore order item: " + e.getMessage(), null);
                        }));
    }

    @Override
    public Uni<ApiResponse<Boolean>> delete(String id) {
        logger.info("Permanently deleting order item id={}", id);
        Attributes attrs = Attributes.builder()
                .put("order_item.id", id)
                .build();

        return runTraced("deleteOrderItemPermanent", "delete_order_item_permanent", attrs,
                () -> orderItemRepository.deletePermanent(id)
                        .chain(item -> {
                            if (item == null) {
                                logger.error("Order item not found for permanent deletion id={}", id);
                                throw new ResourceNotFoundException("Order item not found or not in trash");
                            }
                            return updateOrderTotalPrice(item.getOrderId().toHexString())
                                    .chain(() -> clearCache(item.getOrderId().toHexString()))
                                    .map(v -> true);
                        })
                        .map(deleted -> {
                            logger.info("Order item permanently deleted with id={}", id);
                            return ApiResponse.success("Order item permanently deleted successfully", true);
                        })
                        .onFailure().recoverWithItem(e -> {
                            logger.error("Failed to permanently delete order item id={}", id, e);
                            return new ApiResponse<>("error",
                                    "Failed to permanently delete order item: " + e.getMessage(), false);
                        }));
    }

    @Override
    public Uni<ApiResponse<Boolean>> restoreAll() {
        logger.info("Restoring ALL trashed order items");

        return runTraced("restoreAllOrderItems", "restore_all_order_items", Attributes.empty(),
                () -> orderItemRepository.restoreAllDeleted()
                        .map(restored -> {
                            if (!restored) {
                                throw new ResourceNotFoundException("No order items found in trash");
                            }

                            logger.info("All order items restored successfully");
                            return ApiResponse.success("All order items restored successfully", true);
                        }));
    }

    @Override
    public Uni<ApiResponse<Boolean>> deleteAll() {
        logger.info("Permanently deleting ALL trashed order items");

        return runTraced("deleteAllOrderItems", "delete_all_order_items", Attributes.empty(),
                () -> orderItemRepository.deleteAllDeleted()
                        .map(deleted -> {
                            if (!deleted) {
                                throw new ResourceNotFoundException("No order items found in trash");
                            }

                            logger.info("All order items permanently deleted successfully");
                            return ApiResponse.success("All order items permanently deleted successfully", true);
                        }));
    }

    private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
            java.util.function.Supplier<Uni<T>> supplier) {
        return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
    }
}
