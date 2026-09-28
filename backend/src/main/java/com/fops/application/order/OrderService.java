package com.fops.application.order;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.infrastructure.persistence.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final OrderNotificationRepository orderNotificationRepository;
    private final FulfillmentService fulfillmentService;

    public OrderService(UserRepository userRepository,
                        ItemRepository itemRepository,
                        OrderRepository orderRepository,
                        InventoryMovementRepository inventoryMovementRepository,
                        OrderNotificationRepository orderNotificationRepository,
                        FulfillmentService fulfillmentService) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.orderNotificationRepository = orderNotificationRepository;
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * Creates the order and covers it with available stock: completed, partial or pending depending on stock.
     */
    @Transactional
    public Order createOrder(Long userId, Long itemId, Integer requestedQuantity) {
        if (requestedQuantity == null || requestedQuantity <= 0) {
            throw new BusinessRuleException("Order quantity must be greater than 0");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));

        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item " + itemId + " not found"));

        Order order = orderRepository.save(new Order(user, item, requestedQuantity));
        fulfillmentService.fulfillFromStock(order, item);
        return order;
    }

    /**
     * Lists orders with optional user, item and status filters; newest first.
     */
    @Transactional(readOnly = true)
    public List<Order> findOrders(Long userId, Long itemId, OrderStatus status) {
        Specification<Order> spec = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("item").get("id"), itemId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return orderRepository.findAll(spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return findOrders(null, null, null);
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> findMovementsForOrder(Long orderId) {
        findById(orderId);
        return inventoryMovementRepository.findByOrderIdChronological(orderId);
    }

    @Transactional(readOnly = true)
    public OrderProgress getProgress(Long orderId) {
        Order order = findById(orderId);
        return new OrderProgress(
                order,
                inventoryMovementRepository.findByOrderIdChronological(orderId),
                orderNotificationRepository.findByOrderId(orderId));
    }
}
