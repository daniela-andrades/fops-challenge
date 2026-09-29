package com.fops.application.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fops.domain.enums.MovementType;
import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * POST /api/orders/{id}/cancel through the real stack: compensating movements, re-allocation through the
 * existing fulfilment routine, state rules and the item lock under concurrent cancellations.
 */
@AutoConfigureMockMvc
class OrderCancellationIT extends IntegrationTest {

    private static final String RETURN_REASON = "Returned to stock - order #%d cancelled";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private OrderNotificationRepository notificationRepository;

    @Test
    void cancellingAPendingOrderWithoutAllocationsCreatesNoMovements() throws Exception {
        Item item = fixtures.item(0);
        Order order = fixtures.order(fixtures.user(), item, 4);
        int movementsBefore = movementsOf(item).size();

        MockHttpServletResponse response = cancel(order);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json.readTree(response.getContentAsString()).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(movementsOf(item)).hasSize(movementsBefore);
        assertThat(stockOf(item)).isZero();
    }

    @Test
    void cancellingAPartialOrderReturnsExactlyItsAllocationAndRestoresTheStock() throws Exception {
        Item item = fixtures.item(5);
        int stockBeforeOrder = stockOf(item);
        Order order = fixtures.order(fixtures.user(), item, 8);
        InventoryMovement allocation = outMovementsOf(order).get(0);

        assertThat(cancel(order).getStatus()).isEqualTo(200);

        List<InventoryMovement> orderMovements = movementRepository.findByOrderIdChronological(order.getId());
        assertThat(orderMovements).hasSize(2);
        InventoryMovement returned = orderMovements.get(1);
        assertThat(returned.getMovementType()).isEqualTo(MovementType.IN);
        assertThat(returned.getQuantity()).isEqualTo(allocation.getQuantity()).isEqualTo(5);
        assertThat(returned.getReason()).isEqualTo(RETURN_REASON.formatted(order.getId()));
        assertThat(stockOf(item)).isEqualTo(stockBeforeOrder);

        InventoryMovement originalAfter = movementRepository.findById(allocation.getId()).orElseThrow();
        assertThat(originalAfter.getMovementType()).as("the original allocation is never modified").isEqualTo(MovementType.OUT);
        assertThat(originalAfter.getQuantity()).isEqualTo(5);
        assertThat(originalAfter.getOrder().getId()).isEqualTo(order.getId());

        mvc.perform(get("/api/orders/{id}/movements", order.getId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[1].movementType").value("IN"));
    }

    @Test
    void returnedStockGoesToTheOldestRemainingOpenOrderThroughTheExistingRoutine() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(5);
        Order cancelled = fixtures.order(user, item, 8);   // takes all 5 units, oldest
        Order older = fixtures.order(user, item, 3);       // pending, created before `newer`
        Order newer = fixtures.order(user, item, 4);       // pending

        cancel(cancelled);

        Order olderAfter = orderService.findById(older.getId());
        Order newerAfter = orderService.findById(newer.getId());
        assertThat(olderAfter.getStatus()).as("FIFO: the older open order is served first").isEqualTo(OrderStatus.COMPLETED);
        assertThat(newerAfter.getFulfilledQuantity()).isEqualTo(2);
        assertThat(stockOf(item)).isZero();

        InventoryMovement returned = movementRepository.findByOrderIdChronological(cancelled.getId()).stream()
                .filter(m -> m.getMovementType() == MovementType.IN).findFirst().orElseThrow();
        assertThat(movementRepository.findBySourceMovementId(returned.getId()))
                .as("re-allocated by allocateToOpenOrders, attributed to the exact return")
                .extracting(m -> m.getOrder().getId(), InventoryMovement::getQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(older.getId(), 3), org.assertj.core.groups.Tuple.tuple(newer.getId(), 2));
    }

    @Test
    void eachAllocationGetsItsOwnReturnAndReallocationPointsToIt() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(2);
        Order cancelled = fixtures.order(user, item, 6);   // 2 from stock
        fixtures.incoming(item, 3);                         // +3 from a delivery -> 5/6
        Order waiting = fixtures.order(user, item, 10);

        cancel(cancelled);

        List<InventoryMovement> returns = movementRepository.findByOrderIdChronological(cancelled.getId()).stream()
                .filter(m -> m.getMovementType() == MovementType.IN).toList();
        assertThat(returns).extracting(InventoryMovement::getQuantity).containsExactly(2, 3);
        for (InventoryMovement returned : returns) {
            assertThat(movementRepository.findBySourceMovementId(returned.getId()))
                    .singleElement()
                    .satisfies(out -> {
                        assertThat(out.getOrder().getId()).isEqualTo(waiting.getId());
                        assertThat(out.getQuantity()).isEqualTo(returned.getQuantity());
                    });
        }
        assertThat(orderService.findById(waiting.getId()).getFulfilledQuantity()).isEqualTo(5);
    }

    @Test
    void cancellingSendsNoNotification() throws Exception {
        Item item = fixtures.item(3);
        Order order = fixtures.order(fixtures.user(), item, 5);

        cancel(order);

        assertThat(notificationRepository.findByOrderId(order.getId())).isEmpty();
        await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verifyNoInteractions(mailSender));
    }

    @Test
    void completedOrderCannotBeCancelledAndCancellingTwiceIsAConflict() throws Exception {
        Item item = fixtures.item(10);
        Order completed = fixtures.order(fixtures.user(), item, 2);
        Order open = fixtures.order(fixtures.user(), fixtures.item(0), 2);

        MockHttpServletResponse completedResponse = cancel(completed);
        assertThat(completedResponse.getStatus()).isEqualTo(409);
        assertThat(messageOf(completedResponse)).contains("already completed");
        assertThat(orderService.findById(completed.getId()).getStatus()).isEqualTo(OrderStatus.COMPLETED);

        assertThat(cancel(open).getStatus()).isEqualTo(200);
        MockHttpServletResponse second = cancel(open);
        assertThat(second.getStatus()).isEqualTo(409);
        assertThat(messageOf(second)).isEqualTo("Order " + open.getId() + " is already cancelled");
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/orders/{id}/cancel", 999999)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(messageOf(response)).isEqualTo("Order 999999 not found");
    }

    @Test
    void twoSimultaneousCancellationsReturnTheStockOnlyOnce() throws Exception {
        Item item = fixtures.item(4);
        Order order = fixtures.order(fixtures.user(), item, 6);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return cancel(order);
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<MockHttpServletResponse> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS).getStatus());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(movementRepository.findByOrderIdChronological(order.getId()))
                .filteredOn(m -> m.getMovementType() == MovementType.IN).hasSize(1);
        assertThat(stockOf(item)).isEqualTo(4).isEqualTo(ledgerOf(item));
    }

    @Test
    void cancelledOrderProgressIsNotInflatedByItsOwnReturns() throws Exception {
        Item item = fixtures.item(5);
        Order order = fixtures.order(fixtures.user(), item, 8);

        cancel(order);

        JsonNode progress = json.readTree(mvc.perform(get("/api/orders/{id}/progress", order.getId()))
                .andReturn().getResponse().getContentAsString());
        assertThat(progress.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(progress.get("fulfilledQuantity").asInt()).isEqualTo(5);
        assertThat(progress.get("completionPercent").asDouble()).isEqualTo(62.5);
        assertThat(progress.get("allocationCount").asInt()).isEqualTo(1);
        assertThat(progress.get("allocations")).hasSize(1);
        assertThat(progress.get("allocations").get(0).get("cumulativeFulfilled").asInt()).isEqualTo(5);
        assertThat(progress.get("notification").isNull()).isTrue();
    }

    @Test
    void itemHistoryStillNetsOutToTheStockAfterCancellationsAndReallocations() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(6);
        Order first = fixtures.order(user, item, 4);
        Order second = fixtures.order(user, item, 5);
        fixtures.incoming(item, 2);
        Order third = fixtures.order(user, item, 3);
        cancel(second);
        fixtures.incoming(item, 4);
        cancel(third);

        assertThat(stockOf(item)).isEqualTo(ledgerOf(item)).isGreaterThanOrEqualTo(0);
        assertThat(orderService.findById(first.getId()).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        await().atMost(Duration.ofSeconds(5)).until(() -> notificationRepository.findByOrderId(first.getId())
                .map(n -> n.getStatus() == NotificationStatus.SENT).orElse(false));
        verify(mailSender, atLeastOnce()).send(any(SimpleMailMessage.class));
    }

    private MockHttpServletResponse cancel(Order order) throws Exception {
        return mvc.perform(post("/api/orders/{id}/cancel", order.getId())).andReturn().getResponse();
    }

    private String messageOf(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString()).get("message").asText();
    }

    private List<InventoryMovement> outMovementsOf(Order order) {
        return movementRepository.findByOrderIdChronological(order.getId()).stream()
                .filter(m -> m.getMovementType() == MovementType.OUT).toList();
    }

    private List<InventoryMovement> movementsOf(Item item) {
        return movementRepository.findByItemIdChronological(item.getId());
    }

    private int stockOf(Item item) {
        return itemRepository.findById(item.getId()).orElseThrow().getStockOnHand();
    }

    private int ledgerOf(Item item) {
        return movementsOf(item).stream()
                .mapToInt(m -> m.getMovementType() == MovementType.IN ? m.getQuantity() : -m.getQuantity())
                .sum();
    }
}
