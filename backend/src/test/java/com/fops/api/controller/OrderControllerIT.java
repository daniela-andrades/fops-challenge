package com.fops.api.controller;

import com.fops.application.notification.NotificationService;
import com.fops.application.order.OrderProgress;
import com.fops.application.order.OrderService;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.OrderNotification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.fops.support.TestData.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP contract of /api/orders: status codes, payloads and error format. Services are mocked.
 */
@WebMvcTest(OrderController.class)
class OrderControllerIT {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private OrderService orderService;
    @MockBean
    private NotificationService notificationService;

    @Test
    void createsOrderAndReturnsLocationAndCompletion() throws Exception {
        Order order = order(7, user(1), item(2, 0), 10);
        order.allocate(5);
        when(orderService.createOrder(1L, 2L, 10)).thenReturn(order);

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"itemId\":2,\"requestedQuantity\":10}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/orders/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PARTIALLY_FULFILLED"))
                .andExpect(jsonPath("$.remainingQuantity").value(5))
                .andExpect(jsonPath("$.completionPercent").value(50.0));
    }

    @Test
    void rejectsInvalidPayloadWithFieldErrors() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":2,\"requestedQuantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request data"))
                .andExpect(jsonPath("$.fieldErrors.userId").value("User is required"))
                .andExpect(jsonPath("$.fieldErrors.requestedQuantity").value("Quantity must be greater than 0"));
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.startsWith("Malformed request")));
    }

    @Test
    void mapsNotFoundAndBusinessRuleErrors() throws Exception {
        when(orderService.findById(99L)).thenThrow(new ResourceNotFoundException("Order 99 not found"));
        mvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order 99 not found"));

        when(orderService.createOrder(anyLong(), anyLong(), anyInt())).thenThrow(new BusinessRuleException("Nope"));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"itemId\":2,\"requestedQuantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Nope"));
    }

    @Test
    void passesFiltersToTheService() throws Exception {
        when(orderService.findOrders(1L, null, OrderStatus.PENDING)).thenReturn(List.of());

        mvc.perform(get("/api/orders").param("userId", "1").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(orderService).findOrders(1L, null, OrderStatus.PENDING);
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mvc.perform(get("/api/orders").param("status", "SHIPPED")).andExpect(status().isBadRequest());
    }

    @Test
    void exposesProgressWithAllocationsAndNotification() throws Exception {
        Item item = item(2, 0);
        Order order = order(7, user(1), item, 4);
        order.allocate(1);
        order.allocate(3);
        InventoryMovement first = InventoryMovement.allocation(item, 1, order, null, false, "a");
        InventoryMovement second = InventoryMovement.allocation(item, 3, order, null, true, "b");
        OrderNotification notification = new OrderNotification(order, LocalDateTime.now());
        notification.markSent(LocalDateTime.now());
        when(orderService.getProgress(7L)).thenReturn(new OrderProgress(order, List.of(first, second), Optional.of(notification)));

        mvc.perform(get("/api/orders/7/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercent").value(100.0))
                .andExpect(jsonPath("$.allocationCount").value(2))
                .andExpect(jsonPath("$.completedBySingleMovement").value(false))
                .andExpect(jsonPath("$.allocations[0].cumulativePercent").value(25.0))
                .andExpect(jsonPath("$.allocations[1].cumulativeFulfilled").value(4))
                .andExpect(jsonPath("$.allocations[1].completesOrder").value(true))
                .andExpect(jsonPath("$.notificationSent").value(true))
                .andExpect(jsonPath("$.notification.status").value("SENT"))
                .andExpect(jsonPath("$.notification.recipient").value("user1@test.local"));
    }

    @Test
    void retryRequeuesThenDeliversTheNotification() throws Exception {
        Order order = order(7, user(1), item(2, 0), 1);
        when(orderService.findById(7L)).thenReturn(order);
        when(orderService.getProgress(7L)).thenReturn(new OrderProgress(order, List.of(), Optional.empty()));

        mvc.perform(post("/api/orders/7/notification/retry")).andExpect(status().isOk());

        var inOrder = inOrder(notificationService);
        inOrder.verify(notificationService).requeue(7L);
        inOrder.verify(notificationService).deliver(7L);
    }
}
