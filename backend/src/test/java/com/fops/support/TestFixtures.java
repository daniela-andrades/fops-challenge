package com.fops.support;

import com.fops.application.inventory.InventoryService;
import com.fops.application.item.ItemService;
import com.fops.application.order.OrderService;
import com.fops.application.user.UserService;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import org.springframework.boot.test.context.TestComponent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates test data through the real application services, so fixtures obey the same business rules as production.
 */
@TestComponent
public class TestFixtures {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final UserService userService;
    private final ItemService itemService;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    public TestFixtures(UserService userService, ItemService itemService,
                        OrderService orderService, InventoryService inventoryService) {
        this.userService = userService;
        this.itemService = itemService;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    public User user() {
        int n = SEQUENCE.incrementAndGet();
        return userService.createUser("User " + n, "user" + n + "@test.local");
    }

    public Item item(int initialStock) {
        int n = SEQUENCE.incrementAndGet();
        return itemService.createItem("Item " + n, "SKU-" + n, initialStock);
    }

    public Order order(User user, Item item, int quantity) {
        return orderService.createOrder(user.getId(), item.getId(), quantity);
    }

    public InventoryMovement incoming(Item item, int quantity) {
        return inventoryService.registerIncomingInventory(item.getId(), quantity, "Test delivery");
    }
}
