package com.fops.application.flow;

import com.fops.application.order.OrderService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.Item;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-item row lock must serialize allocations: concurrent orders and deliveries never oversell.
 */
class ConcurrencyIT extends IntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private OrderService orderService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;

    @Test
    void concurrentOrdersNeverAllocateMoreThanTheAvailableStock() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);

        runConcurrently(20, i -> orderService.createOrder(user.getId(), item.getId(), 1));

        var orders = orderService.findOrders(null, item.getId(), null);
        assertThat(orders).hasSize(20);
        assertThat(orders).filteredOn(o -> o.getStatus() == OrderStatus.COMPLETED).hasSize(10);
        assertThat(orders).filteredOn(o -> o.getStatus() == OrderStatus.PENDING).hasSize(10);
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStockOnHand()).isZero();
    }

    @Test
    void concurrentDeliveriesAndOrdersKeepStockConsistentWithTheLedger() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(0);

        runConcurrently(30, i -> {
            if (i % 2 == 0) {
                fixtures.incoming(item, 3);
            } else {
                orderService.createOrder(user.getId(), item.getId(), 2);
            }
        });

        int stock = itemRepository.findById(item.getId()).orElseThrow().getStockOnHand();
        int ledger = movementRepository.findByItemIdChronological(item.getId()).stream()
                .mapToInt(m -> m.getMovementType() == MovementType.IN ? m.getQuantity() : -m.getQuantity())
                .sum();
        int fulfilled = orderService.findOrders(null, item.getId(), null).stream().mapToInt(o -> o.getFulfilledQuantity()).sum();

        assertThat(stock).isEqualTo(ledger).isGreaterThanOrEqualTo(0);
        assertThat(fulfilled + stock).isEqualTo(15 * 3);
    }

    private void runConcurrently(int tasks, IntTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                task.run(index);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    @FunctionalInterface
    private interface IntTask {
        void run(int index) throws Exception;
    }
}
