package com.fops.domain.model;

import com.fops.domain.enums.OrderStatus;
import com.fops.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final User user = user(1);
    private final Item item = item(1, 0);

    @Test
    void newOrderIsPendingWithEverythingRemaining() {
        Order order = new Order(user, item, 20);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getFulfilledQuantity()).isZero();
        assertThat(order.getRemainingQuantity()).isEqualTo(20);
        assertThat(order.getCompletionPercent()).isZero();
        assertThat(order.getCompletedAt()).isNull();
        assertThat(order.isOpen()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveRequestedQuantity(int quantity) {
        assertThatThrownBy(() -> new Order(user, item, quantity))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than 0");
    }

    @Test
    void rejectsNullRequestedQuantity() {
        assertThatThrownBy(() -> new Order(user, item, null)).isInstanceOf(BusinessRuleException.class);
    }

    @DisplayName("status follows completion: 0% pending, 0-100% partial, 100% completed")
    @ParameterizedTest(name = "requested 20, fulfilled {0} -> {1} ({2}%)")
    @CsvSource({
            "0,  PENDING,             0.0",
            "10, PARTIALLY_FULFILLED, 50.0",
            "19, PARTIALLY_FULFILLED, 95.0",
            "20, COMPLETED,           100.0"
    })
    void statusAndCompletionFollowFulfilledQuantity(int fulfilled, OrderStatus expectedStatus, double expectedPercent) {
        Order order = new Order(user, item, 20);
        if (fulfilled > 0) {
            order.allocate(fulfilled);
        }

        assertThat(order.getStatus()).isEqualTo(expectedStatus);
        assertThat(order.getCompletionPercent()).isEqualTo(expectedPercent);
        assertThat(order.getRemainingQuantity()).isEqualTo(20 - fulfilled);
    }

    @Test
    void completesAcrossSeveralAllocationsAndStampsCompletionTime() {
        Order order = new Order(user, item, 8);

        order.allocate(5);
        assertThat(order.getCompletedAt()).isNull();

        order.allocate(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    void completionPercentIsRoundedToTwoDecimals() {
        Order order = new Order(user, item, 3);
        order.allocate(1);

        assertThat(order.getCompletionPercent()).isEqualTo(33.33);
    }

    @Test
    void cannotAllocateMoreThanRemaining() {
        Order order = new Order(user, item, 5);
        order.allocate(4);

        assertThatThrownBy(() -> order.allocate(2))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only 1 remaining");
        assertThat(order.getFulfilledQuantity()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void cannotAllocateNonPositiveQuantity(int quantity) {
        Order order = new Order(user, item, 5);

        assertThatThrownBy(() -> order.allocate(quantity)).isInstanceOf(BusinessRuleException.class);
    }
}
