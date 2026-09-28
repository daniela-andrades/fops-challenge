package com.fops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole API wired together: HTTP -> services -> database -> HTTP, exactly as the frontend uses it.
 */
@AutoConfigureMockMvc
class ApiFlowIT extends IntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void orderLifecycleIsVisibleFromBothTraceabilityEndpoints() throws Exception {
        long userId = create("/api/users", "{\"name\":\"Ana\",\"email\":\"ana@test.local\"}");
        long itemId = create("/api/items", "{\"name\":\"Laptop\",\"sku\":\"lap-1\",\"stockOnHand\":0}");
        long orderId = create("/api/orders", "{\"userId\":%d,\"itemId\":%d,\"requestedQuantity\":5}".formatted(userId, itemId));
        long inId = create("/api/inventory/incoming", "{\"itemId\":%d,\"quantity\":8,\"reason\":\"Supplier\"}".formatted(itemId));

        mvc.perform(get("/api/orders/{id}/progress", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionPercent").value(100.0))
                .andExpect(jsonPath("$.itemSku").value("LAP-1"))
                .andExpect(jsonPath("$.allocations", hasSize(1)))
                .andExpect(jsonPath("$.allocations[0].sourceMovementId").value(inId))
                .andExpect(jsonPath("$.notification.status").exists());

        mvc.perform(get("/api/inventory/movements/{id}", inId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("IN"))
                .andExpect(jsonPath("$.itemName").value("Laptop"))
                .andExpect(jsonPath("$.allocations", hasSize(1)))
                .andExpect(jsonPath("$.allocations[0].orderId").value(orderId))
                .andExpect(jsonPath("$.allocations[0].completesOrder").value(true));

        mvc.perform(get("/api/items/{id}", itemId)).andExpect(jsonPath("$.stockOnHand").value(3));

        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.completedOrders").value(1))
                .andExpect(jsonPath("$.totalStockOnHand").value(3));
    }

    @Test
    void duplicateSkuIsAConflictEvenWithDifferentCase() throws Exception {
        create("/api/items", "{\"name\":\"Laptop\",\"sku\":\"LAP-1\"}");

        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other\",\"sku\":\"lap-1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void orderingForUnknownItemIsNotFound() throws Exception {
        long userId = create("/api/users", "{\"name\":\"Ana\",\"email\":\"ana@test.local\"}");

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"itemId\":999,\"requestedQuantity\":1}".formatted(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Item 999 not found"));
    }

    private long create(String path, String body) throws Exception {
        String response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.get("id").asLong();
    }
}
