package com.fops.api.dto;

import com.fops.domain.enums.MovementType;
import com.fops.domain.model.InventoryMovement;

import java.time.LocalDateTime;

public class InventoryMovementResponse {
    private Long id;
    private Long itemId;
    private Long orderId;
    private Long sourceMovementId;
    private boolean completesOrder;
    private Integer quantity;
    private MovementType movementType;
    private String reason;
    private LocalDateTime createdAt;

    public static InventoryMovementResponse from(InventoryMovement movement) {
        InventoryMovementResponse response = new InventoryMovementResponse();
        response.id = movement.getId();
        response.itemId = movement.getItem().getId();
        response.orderId = movement.getOrder() != null ? movement.getOrder().getId() : null;
        response.sourceMovementId = movement.getSourceMovement() != null ? movement.getSourceMovement().getId() : null;
        response.completesOrder = movement.isCompletesOrder();
        response.quantity = movement.getQuantity();
        response.movementType = movement.getMovementType();
        response.reason = movement.getReason();
        response.createdAt = movement.getCreatedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getSourceMovementId() {
        return sourceMovementId;
    }

    public void setSourceMovementId(Long sourceMovementId) {
        this.sourceMovementId = sourceMovementId;
    }

    public boolean isCompletesOrder() {
        return completesOrder;
    }

    public void setCompletesOrder(boolean completesOrder) {
        this.completesOrder = completesOrder;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
