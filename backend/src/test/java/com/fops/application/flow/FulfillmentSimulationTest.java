package com.fops.application.flow;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.application.inventory.InventoryService;
import com.fops.application.notification.NotificationService;
import com.fops.application.order.OrderCancellationService;
import com.fops.application.order.OrderService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.exception.InvalidOrderStateException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.fops.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Randomized simulation of the business rules: thousands of orders, deliveries and cancellations run through the
 * real services, and every business invariant is checked after every single step.
 *
 * <p>The repositories are in-memory stores (Mockito answers backed by maps), so the services see consistent state
 * as they would with a database; locking and SQL are covered by the integration tests. Each repetition uses its
 * repetition number as the random seed, so any failure names the seed and step that reproduce it.
 */
class FulfillmentSimulationTest {

    private static final int STEPS = 400;
    private static final List<OrderStatus> OPEN = List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FULFILLED);

    private final Map<Long, User> users = new HashMap<>();
    private final Map<Long, Item> items = new LinkedHashMap<>();
    private final Map<Long, Order> orders = new LinkedHashMap<>();
    private final List<InventoryMovement> movements = new ArrayList<>();
    private final List<Long> notifiedOrders = new ArrayList<>();
    private long nextOrderId = 1;
    private long nextMovementId = 1;

    private OrderService orderService;
    private InventoryService inventoryService;
    private OrderCancellationService cancellationService;

    @BeforeEach
    void wireRealServicesOnInMemoryRepositories() {
        UserRepository userRepository = mock(UserRepository.class);
        ItemRepository itemRepository = mock(ItemRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(userRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(users.get(call.<Long>getArgument(0))));
        when(itemRepository.findByIdForUpdate(any())).thenAnswer(call -> Optional.ofNullable(items.get(call.<Long>getArgument(0))));

        when(orderRepository.save(any())).thenAnswer(call -> {
            Order order = call.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", nextOrderId++);
            }
            orders.put(order.getId(), order);
            return order;
        });
        when(orderRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(orders.get(call.<Long>getArgument(0))));
        when(orderRepository.findItemIdById(any())).thenAnswer(call ->
                Optional.ofNullable(orders.get(call.<Long>getArgument(0))).map(order -> order.getItem().getId()));
        when(orderRepository.findByItemAndStatusInOrderByCreatedAtAscIdAsc(any(), anyList())).thenAnswer(call -> {
            Item item = call.getArgument(0);
            List<OrderStatus> statuses = call.getArgument(1);
            return orders.values().stream()
                    .filter(order -> order.getItem().getId().equals(item.getId()) && statuses.contains(order.getStatus()))
                    .sorted(Comparator.comparing(Order::getCreatedAt).thenComparing(Order::getId))
                    .collect(Collectors.toList());
        });

        when(movementRepository.save(any())).thenAnswer(call -> {
            InventoryMovement movement = call.getArgument(0);
            ReflectionTestUtils.setField(movement, "id", nextMovementId++);
            movements.add(movement);
            return movement;
        });
        when(movementRepository.findByOrderIdChronological(any())).thenAnswer(call -> movements.stream()
                .filter(m -> m.getOrder() != null && m.getOrder().getId().equals(call.<Long>getArgument(0)))
                .collect(Collectors.toList()));

        doAnswer(call -> notifiedOrders.add(call.<Order>getArgument(0).getId()))
                .when(notificationService).enqueueOrderCompleted(any());

        FulfillmentService fulfillment = new FulfillmentService(orderRepository, movementRepository, notificationService,
                mock(ApplicationEventPublisher.class));
        orderService = new OrderService(userRepository, itemRepository, orderRepository, movementRepository,
                mock(OrderNotificationRepository.class), fulfillment);
        inventoryService = new InventoryService(itemRepository, movementRepository, fulfillment);
        cancellationService = new OrderCancellationService(orderRepository, itemRepository, movementRepository, fulfillment);
    }

    @RepeatedTest(value = 25, name = "seed {currentRepetition}")
    void businessInvariantsHoldAfterEveryStep(RepetitionInfo repetition) {
        long seed = repetition.getCurrentRepetition();
        Random random = new Random(seed);

        for (long id = 1; id <= 5; id++) {
            users.put(id, user(id));
        }
        for (long id = 1; id <= 4; id++) {
            Item item = new Item("Item " + id, "SKU-" + id);
            ReflectionTestUtils.setField(item, "id", id);
            items.put(id, item);
            int initialStock = random.nextInt(4) * 5;
            if (initialStock > 0) {
                inventoryService.registerIncomingInventory(id, initialStock, "Initial stock");
            }
        }
        checkInvariants(seed, 0, "setup");

        for (int step = 1; step <= STEPS; step++) {
            String operation;
            int dice = random.nextInt(100);
            if (dice < 45) {
                long userId = 1 + random.nextInt(users.size());
                long itemId = 1 + random.nextInt(items.size());
                int quantity = 1 + random.nextInt(12);
                orderService.createOrder(userId, itemId, quantity);
                operation = "order " + quantity + " of item " + itemId;
            } else if (dice < 80 || orders.isEmpty()) {
                long itemId = 1 + random.nextInt(items.size());
                int quantity = 1 + random.nextInt(15);
                inventoryService.registerIncomingInventory(itemId, quantity, "Delivery " + step);
                operation = "deliver " + quantity + " of item " + itemId;
            } else {
                Order target = new ArrayList<>(orders.values()).get(random.nextInt(orders.size()));
                operation = "cancel order " + target.getId() + " (" + target.getStatus() + ")";
                if (target.isOpen()) {
                    cancellationService.cancel(target.getId());
                } else {
                    assertThatThrownBy(() -> cancellationService.cancel(target.getId()))
                            .as(context(seed, step, operation))
                            .isInstanceOf(InvalidOrderStateException.class);
                }
            }
            checkInvariants(seed, step, operation);
        }

        assertThat(orders.values()).as("the simulation reached every status (seed %d)", seed)
                .extracting(Order::getStatus).contains(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
    }

    private void checkInvariants(long seed, int step, String operation) {
        String context = context(seed, step, operation);

        for (Item item : items.values()) {
            List<InventoryMovement> ledger = movements.stream().filter(m -> m.getItem().getId().equals(item.getId())).toList();
            int net = ledger.stream().mapToInt(m -> m.getMovementType() == MovementType.IN ? m.getQuantity() : -m.getQuantity()).sum();
            assertThat(item.getStockOnHand()).as("%s · stock of %s equals its ledger", context, item.getSku()).isEqualTo(net);
            assertThat(item.getStockOnHand()).as("%s · stock of %s never negative", context, item.getSku()).isNotNegative();

            List<Order> open = orders.values().stream()
                    .filter(o -> o.getItem().getId().equals(item.getId()) && OPEN.contains(o.getStatus()))
                    .sorted(Comparator.comparing(Order::getCreatedAt).thenComparing(Order::getId))
                    .toList();
            if (item.getStockOnHand() > 0) {
                assertThat(open).as("%s · %s has stock, so no order of it can be waiting", context, item.getSku()).isEmpty();
            }
            List<Order> partial = open.stream().filter(o -> o.getStatus() == OrderStatus.PARTIALLY_FULFILLED).toList();
            assertThat(partial).as("%s · FIFO: at most one partial order per item", context).hasSizeLessThanOrEqualTo(1);
            if (!partial.isEmpty()) {
                assertThat(open.get(0)).as("%s · FIFO: only the oldest open order can be partial", context).isSameAs(partial.get(0));
            }
        }

        for (Order order : orders.values()) {
            int out = movementsOf(order, MovementType.OUT);
            int returned = movementsOf(order, MovementType.IN);
            assertThat(order.getFulfilledQuantity()).as("%s · order %d never over-allocated", context, order.getId())
                    .isBetween(0, order.getRequestedQuantity());
            assertThat(order.getRemainingQuantity()).as("%s · order %d remaining", context, order.getId())
                    .isEqualTo(order.getRequestedQuantity() - order.getFulfilledQuantity());
            assertThat(out).as("%s · order %d fulfilled equals its OUT movements", context, order.getId())
                    .isEqualTo(order.getFulfilledQuantity());
            if (order.getStatus() == OrderStatus.CANCELLED) {
                assertThat(returned).as("%s · cancelled order %d returned all it had", context, order.getId()).isEqualTo(out);
            } else {
                assertThat(returned).as("%s · open or completed order %d has no returns", context, order.getId()).isZero();
                OrderStatus expected = order.getRemainingQuantity() == 0 ? OrderStatus.COMPLETED
                        : order.getFulfilledQuantity() > 0 ? OrderStatus.PARTIALLY_FULFILLED : OrderStatus.PENDING;
                assertThat(order.getStatus()).as("%s · order %d status follows its quantities", context, order.getId()).isEqualTo(expected);
            }
        }

        for (InventoryMovement movement : movements) {
            if (movement.getSourceMovement() != null) {
                assertThat(movement.getSourceMovement().getMovementType()).as("%s · allocations are fed by IN movements", context)
                        .isEqualTo(MovementType.IN);
                assertThat(movement.getSourceMovement().getItem()).as("%s · fed by the same item", context).isSameAs(movement.getItem());
            }
        }

        Set<Long> completed = orders.values().stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .map(Order::getId).collect(Collectors.toSet());
        assertThat(notifiedOrders).as("%s · one email per completed order, no duplicates", context).doesNotHaveDuplicates();
        assertThat(new HashSet<>(notifiedOrders)).as("%s · exactly the completed orders are notified", context).isEqualTo(completed);
    }

    private int movementsOf(Order order, MovementType type) {
        return movements.stream()
                .filter(m -> m.getOrder() != null && m.getOrder().getId().equals(order.getId()) && m.getMovementType() == type)
                .mapToInt(InventoryMovement::getQuantity).sum();
    }

    private static String context(long seed, int step, String operation) {
        return "seed " + seed + ", step " + step + " (" + operation + ")";
    }
}
