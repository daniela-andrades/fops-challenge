package com.fops.domain.model;

import com.fops.domain.enums.MovementType;
import com.fops.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryMovementTest {

    private final Item item = item(1, 0);

    @Test
    void incomingMovementHasNoOrder() {
        InventoryMovement movement = InventoryMovement.incoming(item, 10, "Supplier delivery");

        assertThat(movement.getMovementType()).isEqualTo(MovementType.IN);
        assertThat(movement.getOrder()).isNull();
        assertThat(movement.getSourceMovement()).isNull();
        assertThat(movement.isCompletesOrder()).isFalse();
        assertThat(movement.getCreatedAt()).isNotNull();
    }

    @Test
    void allocationLinksOrderSourceAndCompletionFlag() {
        Order order = order(7, user(1), item, 5);
        InventoryMovement source = InventoryMovement.incoming(item, 5, "Delivery");

        InventoryMovement allocation = InventoryMovement.allocation(item, 5, order, source, true, "Allocated");

        assertThat(allocation.getMovementType()).isEqualTo(MovementType.OUT);
        assertThat(allocation.getOrder()).isSameAs(order);
        assertThat(allocation.getSourceMovement()).isSameAs(source);
        assertThat(allocation.isCompletesOrder()).isTrue();
    }

    @Test
    void outMovementRequiresAnOrder() {
        assertThatThrownBy(() -> InventoryMovement.allocation(item, 5, null, null, false, "Allocated"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("linked to an order");
    }

    @Test
    void quantityMustBePositive() {
        assertThatThrownBy(() -> InventoryMovement.incoming(item, 0, "Delivery"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
