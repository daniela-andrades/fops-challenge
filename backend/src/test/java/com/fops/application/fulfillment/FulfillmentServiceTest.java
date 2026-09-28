package com.fops.application.fulfillment;

import com.fops.application.notification.NotificationService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.events.OrderCompletedEvent;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FulfillmentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FulfillmentService fulfillmentService;

    private final User user = user(1);

    @BeforeEach
    void saveReturnsTheMovement() {
        lenient().when(inventoryMovementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void fulfillsNewOrderCompletelyWhenStockIsEnough() {
        Item item = item(1, 50);
        Order order = order(10, user, item, 10);

        InventoryMovement movement = fulfillmentService.fulfillFromStock(order, item).orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(item.getStockOnHand()).isEqualTo(40);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.OUT);
        assertThat(movement.getQuantity()).isEqualTo(10);
        assertThat(movement.getOrder()).isSameAs(order);
        assertThat(movement.getSourceMovement()).isNull();
        assertThat(movement.isCompletesOrder()).isTrue();
        verify(notificationService).enqueueOrderCompleted(order);
        verify(eventPublisher).publishEvent(new OrderCompletedEvent(10L));
    }

    @Test
    void fulfillsNewOrderPartiallyWhenStockIsShort() {
        Item item = item(1, 7);
        Order order = order(10, user, item, 10);

        InventoryMovement movement = fulfillmentService.fulfillFromStock(order, item).orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED);
        assertThat(order.getRemainingQuantity()).isEqualTo(3);
        assertThat(item.getStockOnHand()).isZero();
        assertThat(movement.isCompletesOrder()).isFalse();
        verifyNoInteractions(notificationService, eventPublisher);
    }

    @Test
    void leavesNewOrderPendingWithoutMovementWhenThereIsNoStock() {
        Item item = item(1, 0);
        Order order = order(10, user, item, 10);

        assertThat(fulfillmentService.fulfillFromStock(order, item)).isEmpty();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(inventoryMovementRepository, notificationService, eventPublisher);
    }

    @Test
    void distributesIncomingStockToOpenOrdersOldestFirst() {
        Item item = item(1, 10);
        Order oldest = order(1, user, item, 4);
        Order middle = order(2, user, item, 5);
        Order newest = order(3, user, item, 6);
        InventoryMovement delivery = InventoryMovement.incoming(item, 10, "Delivery");
        when(orderRepository.findByItemAndStatusInOrderByCreatedAtAscIdAsc(eq(item), anyList()))
                .thenReturn(List.of(oldest, middle, newest));

        List<InventoryMovement> allocations = fulfillmentService.allocateToOpenOrders(item, delivery);

        assertThat(allocations).extracting(InventoryMovement::getQuantity).containsExactly(4, 5, 1);
        assertThat(allocations).allSatisfy(m -> assertThat(m.getSourceMovement()).isSameAs(delivery));
        assertThat(oldest.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(middle.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(newest.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED);
        assertThat(item.getStockOnHand()).isZero();
        verify(notificationService).enqueueOrderCompleted(oldest);
        verify(notificationService).enqueueOrderCompleted(middle);
        verify(notificationService, never()).enqueueOrderCompleted(newest);
        verify(eventPublisher, times(2)).publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    void keepsLeftoverStockWhenOpenOrdersAreCovered() {
        Item item = item(1, 10);
        Order order = order(1, user, item, 8);
        when(orderRepository.findByItemAndStatusInOrderByCreatedAtAscIdAsc(eq(item), anyList())).thenReturn(List.of(order));

        fulfillmentService.allocateToOpenOrders(item, InventoryMovement.incoming(item, 10, "Delivery"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(item.getStockOnHand()).isEqualTo(2);
    }

    @Test
    void doesNothingWhenThereAreNoOpenOrders() {
        Item item = item(1, 10);
        when(orderRepository.findByItemAndStatusInOrderByCreatedAtAscIdAsc(eq(item), anyList())).thenReturn(List.of());

        assertThat(fulfillmentService.allocateToOpenOrders(item, InventoryMovement.incoming(item, 10, "Delivery"))).isEmpty();
        assertThat(item.getStockOnHand()).isEqualTo(10);
    }
}
