package com.fops.api.dto;

import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.Order;

import java.time.LocalDateTime;

public class OrderResponse {
    private Long id;
    private Long userId;
    private Long itemId;
    private Integer requestedQuantity;
    private Integer fulfilledQuantity;
    private Integer remainingQuantity;
    private double completionPercent;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.userId = order.getUser().getId();
        response.itemId = order.getItem().getId();
        response.requestedQuantity = order.getRequestedQuantity();
        response.fulfilledQuantity = order.getFulfilledQuantity();
        response.remainingQuantity = order.getRemainingQuantity();
        response.completionPercent = order.getCompletionPercent();
        response.status = order.getStatus();
        response.createdAt = order.getCreatedAt();
        response.completedAt = order.getCompletedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(Integer requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public Integer getFulfilledQuantity() {
        return fulfilledQuantity;
    }

    public void setFulfilledQuantity(Integer fulfilledQuantity) {
        this.fulfilledQuantity = fulfilledQuantity;
    }

    public Integer getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(Integer remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public double getCompletionPercent() {
        return completionPercent;
    }

    public void setCompletionPercent(double completionPercent) {
        this.completionPercent = completionPercent;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
