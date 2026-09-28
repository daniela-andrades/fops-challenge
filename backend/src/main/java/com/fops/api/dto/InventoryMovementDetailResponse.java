package com.fops.api.dto;

import com.fops.domain.enums.MovementType;
import com.fops.domain.model.InventoryMovement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Traceability from the movement side: affected item, type, linked order and whether it completed that order.
 * For an IN movement, allocations lists the OUT movements that distributed its stock across orders.
 */
public record InventoryMovementDetailResponse(
        Long id,
        MovementType movementType,
        Integer quantity,
        String reason,
        LocalDateTime createdAt,
        Long itemId,
        String itemName,
        String itemSku,
        OrderResponse order,
        Long sourceMovementId,
        boolean completesOrder,
        List<InventoryMovementResponse> allocations) {

    public static InventoryMovementDetailResponse from(InventoryMovement movement, List<InventoryMovement> allocations) {
        return new InventoryMovementDetailResponse(
                movement.getId(),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getReason(),
                movement.getCreatedAt(),
                movement.getItem().getId(),
                movement.getItem().getName(),
                movement.getItem().getSku(),
                movement.getOrder() != null ? OrderResponse.from(movement.getOrder()) : null,
                movement.getSourceMovement() != null ? movement.getSourceMovement().getId() : null,
                movement.isCompletesOrder(),
                allocations.stream().map(InventoryMovementResponse::from).toList());
    }
}
