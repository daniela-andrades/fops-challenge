package com.fops.domain.model;

import com.fops.domain.enums.OrderStatus;
import com.fops.domain.exception.InvalidOrderStateException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCancellationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final Item item = item(1, 0);

    @Test
    void pendingAndPartialOrdersCanBeCancelled() {
        Order pending = order(1, user(1), item, 5);
        Order partial = order(2, user(1), item, 5);
        partial.allocate(2);

        pending.cancel(NOW);
        partial.cancel(NOW);

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(pending.getCancelledAt()).isEqualTo(NOW);
        assertThat(partial.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(partial.getFulfilledQuantity()).as("keeps what had been allocated").isEqualTo(2);
        assertThat(partial.isOpen()).isFalse();
    }

    @Test
    void completedOrderCannotBeCancelled() {
        Order completed = order(3, user(1), item, 2);
        completed.allocate(2);

        assertThatThrownBy(() -> completed.cancel(NOW))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Order 3 is already completed and its notification was sent; it cannot be cancelled");
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void orderCannotBeCancelledTwice() {
        Order order = order(4, user(1), item, 2);
        order.cancel(NOW);

        assertThatThrownBy(() -> order.cancel(NOW.plusMinutes(1)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Order 4 is already cancelled");
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void cancelledOrderCannotReceiveStock() {
        Order order = order(5, user(1), item, 3);
        order.cancel(NOW);

        assertThatThrownBy(() -> order.allocate(1)).isInstanceOf(InvalidOrderStateException.class);
        assertThat(order.getFulfilledQuantity()).isZero();
    }

    @Test
    void stockReturnIsAnInMovementLinkedToTheOrder() {
        Order order = order(6, user(1), item, 3);

        InventoryMovement returned = InventoryMovement.returnToStock(item, 3, order, "Returned to stock - order #6 cancelled");

        assertThat(returned.getMovementType()).isEqualTo(com.fops.domain.enums.MovementType.IN);
        assertThat(returned.getOrder()).isSameAs(order);
        assertThat(returned.getSourceMovement()).isNull();
        assertThat(returned.isCompletesOrder()).isFalse();
    }
}
