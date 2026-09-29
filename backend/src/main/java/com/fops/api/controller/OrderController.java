package com.fops.api.controller;

import com.fops.api.dto.InventoryMovementResponse;
import com.fops.api.dto.OrderProgressResponse;
import com.fops.api.dto.OrderRequest;
import com.fops.api.dto.OrderResponse;
import com.fops.application.notification.NotificationService;
import com.fops.application.order.OrderService;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.Order;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final NotificationService notificationService;

    public OrderController(OrderService orderService, NotificationService notificationService) {
        this.orderService = orderService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam(required = false) Long userId,
                                         @RequestParam(required = false) Long itemId,
                                         @RequestParam(required = false) OrderStatus status) {
        return orderService.findOrders(userId, itemId, status).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return OrderResponse.from(orderService.findById(id));
    }

    @GetMapping("/{id}/progress")
    public OrderProgressResponse getOrderProgress(@PathVariable Long id) {
        return OrderProgressResponse.from(orderService.getProgress(id));
    }

    /**
     * Manual retry of the completion email (e.g. after automatic retries are exhausted).
     */
    @PostMapping("/{id}/notification/retry")
    public OrderProgressResponse retryNotification(@PathVariable Long id) {
        orderService.findById(id);
        notificationService.requeue(id);
        notificationService.deliver(id);
        return OrderProgressResponse.from(orderService.getProgress(id));
    }

    @GetMapping("/{id}/movements")
    public List<InventoryMovementResponse> getOrderMovements(@PathVariable Long id) {
        return orderService.findMovementsForOrder(id).stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    /**
     * Creates an order. With an Idempotency-Key, a retry of the same request returns the original order with 200
     * instead of creating (and allocating stock for) a second one; the same key with a different request is a 409.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request,
                                                     @RequestHeader(value = IdempotencyKey.HEADER, required = false) String idempotencyKey) {
        String requestId = IdempotencyKey.normalize(idempotencyKey);
        Order order;
        try {
            order = requestId == null
                    ? orderService.createOrder(request.getUserId(), request.getItemId(), request.getRequestedQuantity())
                    : orderService.createOrder(request.getUserId(), request.getItemId(), request.getRequestedQuantity(), requestId);
        } catch (DataIntegrityViolationException duplicate) {
            // The failed transaction has rolled back; the original order is read in a new transaction.
            Order existing = requestId == null ? null : orderService.findByRequestId(requestId).orElse(null);
            if (existing == null) {
                throw duplicate;
            }
            IdempotencyKey.replayOf(requestId)
                    .compare("userId", existing.getUser().getId(), request.getUserId())
                    .compare("itemId", existing.getItem().getId(), request.getItemId())
                    .compare("requestedQuantity", existing.getRequestedQuantity(), request.getRequestedQuantity())
                    .requireSameRequest();
            return ResponseEntity.ok(OrderResponse.from(existing));
        }
        return ResponseEntity
                .created(URI.create("/api/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }
}
