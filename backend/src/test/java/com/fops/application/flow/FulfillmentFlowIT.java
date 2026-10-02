package com.fops.application.flow;

import com.fops.application.order.OrderService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end business scenarios described under "How fulfillment works" in the README, through the real services and database.
 */
class FulfillmentFlowIT extends IntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = fixtures.user();
    }

    @Test
    @DisplayName("Case 1: an order is completed when stock is available")
    void orderIsCompletedWhenStockIsAvailable() {
        Item item = fixtures.item(50);

        Order order = fixtures.order(user, item, 10);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getFulfilledQuantity()).isEqualTo(10);
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(stockOf(item)).isEqualTo(40);
        assertThat(movementRepository.findByOrderIdChronological(order.getId()))
                .singleElement()
                .satisfies(out -> {
                    assertThat(out.getMovementType()).isEqualTo(MovementType.OUT);
                    assertThat(out.getQuantity()).isEqualTo(10);
                    assertThat(out.isCompletesOrder()).isTrue();
                });
    }

    @Test
    @DisplayName("Case 2: an order is partially fulfilled when stock is short")
    void orderIsPartiallyFulfilledWhenStockIsShort() {
        Item item = fixtures.item(7);

        Order order = fixtures.order(user, item, 10);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED);
        assertThat(order.getRemainingQuantity()).isEqualTo(3);
        assertThat(order.getCompletionPercent()).isEqualTo(70.0);
        assertThat(stockOf(item)).isZero();
    }

    @Test
    @DisplayName("An order stays pending and consumes nothing when there is no stock")
    void orderStaysPendingWithoutStock() {
        Item item = fixtures.item(0);

        Order order = fixtures.order(user, item, 10);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(movementRepository.findByOrderIdChronological(order.getId())).isEmpty();
    }

    @Test
    @DisplayName("Case 3: incoming stock fills a pending order and keeps the leftover")
    void incomingStockFillsPendingOrder() {
        Item item = fixtures.item(0);
        Order order = fixtures.order(user, item, 8);

        InventoryMovement delivery = fixtures.incoming(item, 10);

        Order updated = orderService.findById(order.getId());
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(stockOf(item)).isEqualTo(2);
        assertThat(movementRepository.findBySourceMovementId(delivery.getId()))
                .singleElement()
                .satisfies(out -> assertThat(out.getOrder().getId()).isEqualTo(order.getId()));
    }

    @Test
    @DisplayName("Incoming stock is allocated to open orders oldest first")
    void incomingStockIsAllocatedFifo() {
        Item item = fixtures.item(0);
        Order first = fixtures.order(user, item, 8);
        Order second = fixtures.order(user, item, 5);
        Order third = fixtures.order(user, item, 5);

        fixtures.incoming(item, 10);

        assertThat(statusOf(first)).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderService.findById(second.getId()).getFulfilledQuantity()).isEqualTo(2);
        assertThat(statusOf(third)).isEqualTo(OrderStatus.PENDING);
        assertThat(stockOf(item)).isZero();
    }

    @Test
    @DisplayName("Case 5: an order completed by several deliveries traces every movement back to its source")
    void orderCompletedAcrossDeliveriesIsFullyTraceable() {
        Item item = fixtures.item(0);
        Order order = fixtures.order(user, item, 5);

        InventoryMovement firstDelivery = fixtures.incoming(item, 2);
        InventoryMovement secondDelivery = fixtures.incoming(item, 3);

        List<InventoryMovement> covering = movementRepository.findByOrderIdChronological(order.getId());
        assertThat(covering).extracting(InventoryMovement::getQuantity).containsExactly(2, 3);
        assertThat(covering).extracting(m -> m.getSourceMovement().getId())
                .containsExactly(firstDelivery.getId(), secondDelivery.getId());
        assertThat(covering).extracting(InventoryMovement::isCompletesOrder).containsExactly(false, true);

        var progress = orderService.getProgress(order.getId());
        assertThat(progress.order().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(progress.movements()).hasSize(2);
    }

    @Test
    @DisplayName("Initial stock is recorded as an IN movement")
    void initialStockIsRecordedAsIncomingMovement() {
        Item item = fixtures.item(25);

        assertThat(movementRepository.findByItemIdChronological(item.getId()))
                .singleElement()
                .satisfies(in -> {
                    assertThat(in.getMovementType()).isEqualTo(MovementType.IN);
                    assertThat(in.getQuantity()).isEqualTo(25);
                    assertThat(in.getReason()).isEqualTo("Initial stock");
                });
    }

    @Test
    @DisplayName("Stock on hand always equals the sum of IN minus OUT movements")
    void stockMatchesTheMovementLedger() {
        Item item = fixtures.item(4);
        fixtures.order(user, item, 3);
        fixtures.order(user, item, 6);
        fixtures.incoming(item, 9);
        fixtures.order(user, item, 1);

        int ledger = movementRepository.findByItemIdChronological(item.getId()).stream()
                .mapToInt(m -> m.getMovementType() == MovementType.IN ? m.getQuantity() : -m.getQuantity())
                .sum();
        assertThat(stockOf(item)).isEqualTo(ledger).isGreaterThanOrEqualTo(0);
    }

    private int stockOf(Item item) {
        return itemRepository.findById(item.getId()).orElseThrow().getStockOnHand();
    }

    private OrderStatus statusOf(Order order) {
        return orderService.findById(order.getId()).getStatus();
    }
}
