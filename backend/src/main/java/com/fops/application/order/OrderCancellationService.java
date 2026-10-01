package com.fops.application.order;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancels an open order. An order that consumed stock is never deleted: each of its allocations is returned
 * through a compensating IN movement, and the returned units are re-allocated to the remaining open orders
 * by the existing {@link FulfillmentService#allocateToOpenOrders} routine.
 */
@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final InventoryMovementRepository movementRepository;
    private final FulfillmentService fulfillmentService;

    public OrderCancellationService(OrderRepository orderRepository,
                                    ItemRepository itemRepository,
                                    InventoryMovementRepository movementRepository,
                                    FulfillmentService fulfillmentService) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.fulfillmentService = fulfillmentService;
    }

    @Transactional
    public Order cancel(Long orderId) {
        Long itemId = orderRepository.findItemIdById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));

        // The same per-item lock as allocation, taken before the order or any stock figure is read: a concurrent
        // cancellation or delivery for this item waits here and then sees the committed state.
        Item item = itemRepository.findByIdForUpdate(itemId).orElseThrow();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
        List<InventoryMovement> allocations = movementRepository.findByOrderIdChronological(orderId).stream()
                .filter(movement -> movement.getMovementType() == MovementType.OUT)
                .toList();

        // Cancelled first, so the fulfilment routine no longer sees this order as open.
        order.cancel(LocalDateTime.now());

        for (InventoryMovement allocation : allocations) {
            item.increaseStock(allocation.getQuantity());
            InventoryMovement returned = movementRepository.save(InventoryMovement.returnToStock(
                    item, allocation.getQuantity(), order, "Returned to stock - order #" + orderId + " cancelled"));
            // Re-allocated per return, so every unit given to another order points to the exact return it came from.
            fulfillmentService.allocateToOpenOrders(item, returned);
        }
        return order;
    }
}
