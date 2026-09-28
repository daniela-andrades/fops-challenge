package com.fops.api.controller;

import com.fops.application.dashboard.DashboardService;
import com.fops.application.dashboard.DashboardService.DashboardSummary;
import com.fops.application.inventory.InventoryService;
import com.fops.application.item.ItemService;
import com.fops.application.user.UserService;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.fops.support.TestData.item;
import static com.fops.support.TestData.user;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP contract of users, items, inventory and dashboard endpoints.
 */
@WebMvcTest({UserController.class, ItemController.class, InventoryController.class, DashboardController.class})
class CatalogControllerIT {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private UserService userService;
    @MockBean
    private ItemService itemService;
    @MockBean
    private InventoryService inventoryService;
    @MockBean
    private DashboardService dashboardService;

    @Test
    void createsUser() throws Exception {
        when(userService.createUser("Ana", "ana@test.local")).thenReturn(user(3));

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@test.local\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/3"));
    }

    @Test
    void rejectsInvalidEmailAndDuplicates() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email is not a valid address"));

        when(userService.createUser(any(), any())).thenThrow(new DuplicateResourceException("A user with this email already exists: ana@test.local"));
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@test.local\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A user with this email already exists: ana@test.local"));
    }

    @Test
    void createsItemAndRejectsNegativeStock() throws Exception {
        when(itemService.createItem("Laptop", "LAP-1", 5)).thenReturn(item(4, 5));
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"sku\":\"LAP-1\",\"stockOnHand\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockOnHand").value(5));

        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"sku\":\"LAP-1\",\"stockOnHand\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stockOnHand").value("Initial stock cannot be negative"));
    }

    @Test
    void incomingInventoryUsesDefaultReasonWhenBlank() throws Exception {
        Item item = item(4, 0);
        when(inventoryService.registerIncomingInventory(4L, 10, "Inventory restock"))
                .thenReturn(InventoryMovement.incoming(item, 10, "Inventory restock"));

        mvc.perform(post("/api/inventory/incoming").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":4,\"quantity\":10,\"reason\":\"  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType").value("IN"));

        verify(inventoryService).registerIncomingInventory(4L, 10, "Inventory restock");
    }

    @Test
    void lockTimeoutsAreReportedAsConflicts() throws Exception {
        when(inventoryService.registerIncomingInventory(anyLong(), anyInt(), any()))
                .thenThrow(new PessimisticLockingFailureException("lock timeout"));

        mvc.perform(post("/api/inventory/incoming").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":4,\"quantity\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The item is being modified by another operation, please try again"));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        when(dashboardService.getSummary()).thenThrow(new IllegalStateException("secret internals"));

        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void returnsDashboardSummary() throws Exception {
        when(dashboardService.getSummary()).thenReturn(new DashboardSummary(1, 2, 30, 1, 5, 1, 2, 2, 7, 2, 0, 0));

        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openDemand").value(7))
                .andExpect(jsonPath("$.notificationsSent").value(2));
    }

    @Test
    void unknownRoutesStay404() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
    }
}
