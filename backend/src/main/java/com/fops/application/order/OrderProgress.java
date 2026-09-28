package com.fops.application.order;

import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Order;
import com.fops.domain.model.OrderNotification;

import java.util.List;
import java.util.Optional;

/**
 * Fulfillment view of an order: the order, the OUT movements that covered it and its completion notification (if any).
 */
public record OrderProgress(Order order, List<InventoryMovement> movements, Optional<OrderNotification> notification) {
}
