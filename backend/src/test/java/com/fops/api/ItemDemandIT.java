package com.fops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fops.application.order.OrderCancellationService;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * GET /api/items reports each item's outstanding demand: the remaining quantity of its open
 * (PENDING and PARTIALLY_FULFILLED) orders. Other item endpoints keep their payload unchanged.
 */
@AutoConfigureMockMvc
class ItemDemandIT extends IntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private OrderCancellationService cancellationService;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void itemWithoutOpenOrdersReportsZero() throws Exception {
        Item item = fixtures.item(10);

        assertThat(demandOf(item)).isZero();
    }

    /**
     * Through the API an item never has two partial orders at once (FIFO only ever leaves the oldest open order
     * partial), so both are persisted directly to exercise the aggregate itself.
     */
    @Test
    void twoPartialOrdersAddUpTheirRemainingQuantities() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(0);
        Order first = new Order(user, item, 6);
        first.allocate(2);                                       // 4 remaining
        Order second = new Order(user, item, 10);
        second.allocate(7);                                      // 3 remaining
        orderRepository.save(first);
        orderRepository.save(second);

        assertThat(demandOf(item)).isEqualTo(4 + 3);
    }

    @Test
    void completedAndCancelledOrdersContributeNothing() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(5);
        fixtures.order(user, item, 5);                          // completed from stock
        Order cancelled = fixtures.order(user, item, 7);        // pending
        cancellationService.cancel(cancelled.getId());
        fixtures.order(user, item, 2);                          // pending, the only demand left

        assertThat(demandOf(item)).isEqualTo(2);
    }

    @Test
    void cancellingAnOrderRemovesItsDemand() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(3);
        Order partial = fixtures.order(user, item, 8);          // 3 allocated, 5 remaining
        fixtures.order(user, item, 4);                          // pending, 4 remaining
        assertThat(demandOf(item)).isEqualTo(9);

        cancellationService.cancel(partial.getId());            // its 3 units go to the other order: 1 remaining

        assertThat(demandOf(item)).isEqualTo(1);
    }

    @Test
    void demandIsReportedPerItem() throws Exception {
        User user = fixtures.user();
        Item busy = fixtures.item(0);
        Item quiet = fixtures.item(0);
        fixtures.order(user, busy, 3);
        fixtures.order(user, busy, 4);

        assertThat(demandOf(busy)).isEqualTo(7);
        assertThat(demandOf(quiet)).isZero();
    }

    @Test
    void otherItemEndpointsKeepTheirPayload() throws Exception {
        Item item = fixtures.item(0);
        fixtures.order(fixtures.user(), item, 3);

        JsonNode single = json.readTree(mvc.perform(get("/api/items/{id}", item.getId())).andReturn().getResponse().getContentAsString());

        assertThat(single.has("outstandingDemand")).isFalse();
        assertThat(single.get("sku").asText()).isEqualTo(item.getSku());
    }

    private long demandOf(Item item) throws Exception {
        JsonNode items = json.readTree(mvc.perform(get("/api/items")).andReturn().getResponse().getContentAsString());
        for (JsonNode node : items) {
            if (node.get("id").asLong() == item.getId()) {
                assertThat(node.has("outstandingDemand")).isTrue();
                return node.get("outstandingDemand").asLong();
            }
        }
        throw new AssertionError("item " + item.getId() + " not listed");
    }
}
