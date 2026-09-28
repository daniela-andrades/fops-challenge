package com.fops.support;

import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * In-memory domain objects for unit tests (no database). Ids are assigned by hand where a test needs them.
 */
public final class TestData {

    private TestData() {
    }

    public static User user(long id) {
        User user = new User("User " + id, "user" + id + "@test.local");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Item item(long id, int stock) {
        Item item = new Item("Item " + id, "SKU-" + id);
        ReflectionTestUtils.setField(item, "id", id);
        if (stock > 0) {
            item.increaseStock(stock);
        }
        return item;
    }

    public static Order order(long id, User user, Item item, int quantity) {
        Order order = new Order(user, item, quantity);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
