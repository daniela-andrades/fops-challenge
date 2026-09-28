package com.fops.infrastructure.persistence;

import com.fops.application.order.OrderService;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the core flow against the production database engine (PostgreSQL 16), which H2 only emulates:
 * row locks (SELECT ... FOR UPDATE), check constraints and generated schema.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresIT extends IntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void runsOnPostgres() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 16");
    }

    @Test
    void fulfillmentFlowWorksWithRealRowLocks() {
        var user = fixtures.user();
        Item item = fixtures.item(0);
        Order order = fixtures.order(user, item, 4);

        fixtures.incoming(item, 6);

        assertThat(orderService.findById(order.getId()).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(jdbc.queryForObject("select stock_on_hand from items where id = ?", Integer.class, item.getId())).isEqualTo(2);
    }

    @Test
    void checkConstraintRejectsNegativeStock() {
        Item item = fixtures.item(1);

        assertThatThrownBy(() -> jdbc.update("update items set stock_on_hand = -1 where id = ?", item.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
