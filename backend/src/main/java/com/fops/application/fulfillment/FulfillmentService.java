package com.fops.application.fulfillment;

import com.fops.application.notification.NotificationService;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.events.OrderCompletedEvent;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The only component that allocates stock to orders. Callers must hold the item lock
 * (ItemRepository.findByIdForUpdate) within the same transaction.
 */
@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);
    private static final List<OrderStatus> OPEN_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FULFILLED);

    private final OrderRepository orderRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public FulfillmentService(OrderRepository orderRepository,
                              InventoryMovementRepository inventoryMovementRepository,
                              NotificationService notificationService,
                              ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Allocates the item's available stock to a newly created order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<InventoryMovement> fulfillFromStock(Order order, Item item) {
        int quantity = Math.min(item.getStockOnHand(), order.getRemainingQuantity());
        if (quantity <= 0) {
            return Optional.empty();
        }
        return Optional.of(allocate(order, item, quantity, null, "Allocated from stock on order creation"));
    }

    /**
     * Distributes the item's available stock across its open orders, oldest first (FIFO).
     *
     * @param source IN movement that brought the stock, to trace which delivery fed each order
     * @return the OUT movements created
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<InventoryMovement> allocateToOpenOrders(Item item, InventoryMovement source) {
        List<Order> openOrders = orderRepository.findByItemAndStatusInOrderByCreatedAtAscIdAsc(item, OPEN_STATUSES);

        List<InventoryMovement> allocations = new ArrayList<>();
        for (Order order : openOrders) {
            if (item.getStockOnHand() == 0) {
                break;
            }
            int quantity = Math.min(item.getStockOnHand(), order.getRemainingQuantity());
            allocations.add(allocate(order, item, quantity, source, "Allocated from incoming inventory to open order"));
        }
        return allocations;
    }

    private InventoryMovement allocate(Order order, Item item, int quantity, InventoryMovement source, String reason) {
        item.decreaseStock(quantity);
        order.allocate(quantity);

        boolean completesOrder = order.getStatus() == OrderStatus.COMPLETED;
        InventoryMovement movement = inventoryMovementRepository.save(
                InventoryMovement.allocation(item, quantity, order, source, completesOrder, reason));

        log.info("Allocated {} units of item {} to order {} ({}%, {})",
                quantity, item.getSku(), order.getId(), order.getCompletionPercent(), order.getStatus());

        if (completesOrder) {
            notificationService.enqueueOrderCompleted(order);
            eventPublisher.publishEvent(new OrderCompletedEvent(order.getId()));
        }
        return movement;
    }
}
