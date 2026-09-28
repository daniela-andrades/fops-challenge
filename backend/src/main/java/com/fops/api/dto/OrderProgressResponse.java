package com.fops.api.dto;

import com.fops.application.order.OrderProgress;
import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order completion and its fulfillment history: which movements covered it,
 * how much each contributed and the cumulative progress after each allocation.
 */
public record OrderProgressResponse(
        Long orderId,
        Long userId,
        String userEmail,
        Long itemId,
        String itemSku,
        Integer requestedQuantity,
        Integer fulfilledQuantity,
        Integer remainingQuantity,
        double completionPercent,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        int allocationCount,
        Boolean completedBySingleMovement,
        boolean notificationSent,
        LocalDateTime notificationSentAt,
        Notification notification,
        List<Allocation> allocations) {

    /**
     * Completion email status; null while the order is not completed.
     */
    public record Notification(
            NotificationStatus status,
            String recipient,
            int attempts,
            LocalDateTime lastAttemptAt,
            LocalDateTime nextAttemptAt,
            LocalDateTime sentAt,
            String lastError) {
    }

    public record Allocation(
            Long movementId,
            Long sourceMovementId,
            Integer quantity,
            LocalDateTime allocatedAt,
            int cumulativeFulfilled,
            double cumulativePercent,
            boolean completesOrder) {
    }

    public static OrderProgressResponse from(OrderProgress progress) {
        Order order = progress.order();

        List<Allocation> allocations = new ArrayList<>();
        int cumulative = 0;
        for (InventoryMovement movement : progress.movements()) {
            cumulative += movement.getQuantity();
            allocations.add(new Allocation(
                    movement.getId(),
                    movement.getSourceMovement() != null ? movement.getSourceMovement().getId() : null,
                    movement.getQuantity(),
                    movement.getCreatedAt(),
                    cumulative,
                    Math.round(cumulative * 10000.0 / order.getRequestedQuantity()) / 100.0,
                    movement.isCompletesOrder()));
        }

        Boolean completedBySingleMovement = order.getStatus() == OrderStatus.COMPLETED
                ? allocations.size() == 1
                : null;

        return new OrderProgressResponse(
                order.getId(),
                order.getUser().getId(),
                order.getUser().getEmail(),
                order.getItem().getId(),
                order.getItem().getSku(),
                order.getRequestedQuantity(),
                order.getFulfilledQuantity(),
                order.getRemainingQuantity(),
                order.getCompletionPercent(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                allocations.size(),
                completedBySingleMovement,
                progress.notification().map(n -> n.getStatus() == NotificationStatus.SENT).orElse(false),
                progress.notification().map(n -> n.getSentAt()).orElse(null),
                progress.notification().map(n -> new Notification(
                        n.getStatus(), n.getRecipient(), n.getAttempts(), n.getLastAttemptAt(),
                        n.getNextAttemptAt(), n.getSentAt(), n.getLastError())).orElse(null),
                allocations);
    }
}
