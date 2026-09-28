package com.fops.domain.model;

import com.fops.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTest {

    @Test
    void newItemStartsWithoutStock() {
        assertThat(new Item("Laptop", "LAP-1").getStockOnHand()).isZero();
    }

    @Test
    void increasesAndDecreasesStock() {
        Item item = new Item("Laptop", "LAP-1");

        item.increaseStock(10);
        item.decreaseStock(4);

        assertThat(item.getStockOnHand()).isEqualTo(6);
    }

    @Test
    void stockCanReachZeroButNeverGoesNegative() {
        Item item = new Item("Laptop", "LAP-1");
        item.increaseStock(3);

        item.decreaseStock(3);
        assertThat(item.getStockOnHand()).isZero();

        assertThatThrownBy(() -> item.decreaseStock(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient stock");
        assertThat(item.getStockOnHand()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void rejectsNonPositiveMovements(int quantity) {
        Item item = new Item("Laptop", "LAP-1");

        assertThatThrownBy(() -> item.increaseStock(quantity)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> item.decreaseStock(quantity)).isInstanceOf(BusinessRuleException.class);
    }
}
