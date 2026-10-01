package com.fops.api;

import com.fops.domain.model.Item;
import com.fops.domain.model.User;
import com.fops.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/items issues a constant number of SQL statements however many items exist: no query per item.
 * Hibernate statistics are enabled for this test class only.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ItemListQueryCountIT extends IntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void listingItemsWithDemandIssuesTheSameNumberOfQueriesForFewOrManyItems() throws Exception {
        User user = fixtures.user();
        createItemsWithOpenOrders(user, 3);
        long withFewItems = statementsFor("/api/items");

        createItemsWithOpenOrders(user, 30);
        long withManyItems = statementsFor("/api/items");

        assertThat(withFewItems).as("items + one demand aggregate").isEqualTo(2);
        assertThat(withManyItems).isEqualTo(withFewItems);
    }

    private void createItemsWithOpenOrders(User user, int count) {
        for (int i = 0; i < count; i++) {
            Item item = fixtures.item(0);              // no stock: orders stay pending, so no emails run in the background
            fixtures.order(user, item, 2);
        }
    }

    private long statementsFor(String path) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get(path)).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }
}
