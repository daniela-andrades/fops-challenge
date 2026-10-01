package com.fops.api.controller;

import com.fops.api.dto.OrderRequest;
import com.fops.api.dto.OrderResponse;
import com.fops.application.notification.NotificationService;
import com.fops.application.order.OrderService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The idempotent-creation branch of OrderController, without Spring: what happens when the unique
 * request_id rejects a retry.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerIdempotencyTest {

    private static final String KEY = "6f1c2b9e-3a4d-4e5f-8a7b-9c0d1e2f3a4b";

    @Mock
    private OrderService orderService;
    @Mock
    private NotificationService notificationService;

    private OrderController controller;
    private final Item item = item(2, 0);
    private Order original;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, notificationService);
        original = order(41, user(1), item, 3);
    }

    @Test
    void newOrderWithAKeyIsCreatedWith201AndTheKeyIsPassedOn() {
        when(orderService.createOrder(1L, 2L, 3, KEY)).thenReturn(original);

        ResponseEntity<OrderResponse> response = controller.createOrder(request(1L, 2L, 3), KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/orders/41");
        verify(orderService, never()).createOrder(anyLong(), anyLong(), anyInt());
    }

    @Test
    void retryHittingTheUniqueKeyReturnsTheOriginalOrderWith200() {
        when(orderService.createOrder(1L, 2L, 3, KEY)).thenThrow(new DataIntegrityViolationException("uk request_id"));
        when(orderService.findByRequestId(KEY)).thenReturn(Optional.of(original));

        ResponseEntity<OrderResponse> response = controller.createOrder(request(1L, 2L, 3), KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(41L);
    }

    @Test
    void sameKeyForADifferentOrderIsAConflictNamingEveryMismatch() {
        when(orderService.createOrder(anyLong(), anyLong(), anyInt(), eq(KEY))).thenThrow(new DataIntegrityViolationException("uk"));
        when(orderService.findByRequestId(KEY)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> controller.createOrder(request(9L, 2L, 5), KEY))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Idempotency-Key " + KEY + " was already used for a different request: "
                        + "userId 1 stored, 9 requested; requestedQuantity 3 stored, 5 requested");
    }

    @Test
    void integrityViolationThatIsNotADuplicateKeyIsRethrown() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("fk user");
        when(orderService.createOrder(1L, 2L, 3, KEY)).thenThrow(other);
        when(orderService.findByRequestId(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.createOrder(request(1L, 2L, 3), KEY)).isSameAs(other);
    }

    @Test
    void withoutAKeyTheOriginalMethodIsCalledAndViolationsAreNotSwallowed() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("fk");
        when(orderService.createOrder(1L, 2L, 3)).thenThrow(violation);

        assertThatThrownBy(() -> controller.createOrder(request(1L, 2L, 3), null)).isSameAs(violation);
        verify(orderService, never()).findByRequestId(any());
        verify(orderService, never()).createOrder(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void blankKeyMeansNoKey() {
        when(orderService.createOrder(1L, 2L, 3)).thenReturn(original);

        assertThat(controller.createOrder(request(1L, 2L, 3), "   ").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void oversizedKeyIsRejectedBeforeAnythingIsCreated() {
        assertThatThrownBy(() -> controller.createOrder(request(1L, 2L, 3), "k".repeat(65)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(orderService);
    }

    private static OrderRequest request(Long userId, Long itemId, Integer quantity) {
        OrderRequest request = new OrderRequest();
        request.setUserId(userId);
        request.setItemId(itemId);
        request.setRequestedQuantity(quantity);
        return request;
    }
}
