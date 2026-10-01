package com.fops.application.order;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.enums.OrderStatus;
import com.fops.domain.exception.InvalidOrderStateException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private InventoryMovementRepository movementRepository;
    @Mock
    private FulfillmentService fulfillmentService;

    @InjectMocks
    private OrderCancellationService service;

    private Item item;
    private Order order;

    @BeforeEach
    void setUp() {
        item = item(1, 0);
        order = order(7, user(1), item, 8);
    }

    private void orderExists(List<InventoryMovement> movements) {
        when(orderRepository.findItemIdById(7L)).thenReturn(Optional.of(1L));
        when(itemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(movementRepository.findByOrderIdChronological(7L)).thenReturn(movements);
    }

    @Nested
    @DisplayName("lock ordering")
    class LockOrdering {

        @Test
        void locksTheItemBeforeLoadingTheOrderOrItsMovements() {
            orderExists(List.of());

            service.cancel(7L);

            InOrder inOrder = inOrder(orderRepository, itemRepository, movementRepository);
            inOrder.verify(orderRepository).findItemIdById(7L);
            inOrder.verify(itemRepository).findByIdForUpdate(1L);
            inOrder.verify(orderRepository).findById(7L);
            inOrder.verify(movementRepository).findByOrderIdChronological(7L);
        }
    }

    @Nested
    @DisplayName("compensating movements")
    class CompensatingMovements {

        @Test
        void pendingOrderWithoutAllocationsCreatesNoMovementAndAllocatesNothing() {
            orderExists(List.of());

            Order cancelled = service.cancel(7L);

            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(movementRepository, never()).save(any());
            verifyNoInteractions(fulfillmentService);
        }

        @Test
        void eachAllocationGetsItsOwnReturnAndIsReallocatedWithThatReturnAsSource() {
            order.allocate(2);
            order.allocate(3);
            InventoryMovement first = InventoryMovement.allocation(item, 2, order, null, false, "a");
            InventoryMovement second = InventoryMovement.allocation(item, 3, order, null, false, "b");
            InventoryMovement unrelatedIn = InventoryMovement.incoming(item, 9, "ignored");
            orderExists(List.of(first, unrelatedIn, second));
            when(movementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.cancel(7L);

            ArgumentCaptor<InventoryMovement> returns = ArgumentCaptor.forClass(InventoryMovement.class);
            verify(movementRepository, times(2)).save(returns.capture());
            assertThat(returns.getAllValues()).extracting(InventoryMovement::getQuantity).containsExactly(2, 3);
            assertThat(returns.getAllValues()).allSatisfy(returned -> {
                assertThat(returned.getMovementType()).isEqualTo(MovementType.IN);
                assertThat(returned.getOrder()).isSameAs(order);
                assertThat(returned.getReason()).isEqualTo("Returned to stock - order #7 cancelled");
            });

            InOrder perReturn = inOrder(movementRepository, fulfillmentService);
            perReturn.verify(movementRepository).save(returns.getAllValues().get(0));
            perReturn.verify(fulfillmentService).allocateToOpenOrders(item, returns.getAllValues().get(0));
            perReturn.verify(movementRepository).save(returns.getAllValues().get(1));
            perReturn.verify(fulfillmentService).allocateToOpenOrders(item, returns.getAllValues().get(1));
        }

        @Test
        void returnedUnitsAreBackInStockBeforeTheRoutineRunsAndTheOrderIsAlreadyCancelled() {
            order.allocate(5);
            orderExists(List.of(InventoryMovement.allocation(item, 5, order, null, false, "a")));
            when(movementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            doAnswer(invocation -> {
                assertThat(item.getStockOnHand()).as("stock seen by the routine").isEqualTo(5);
                assertThat(order.getStatus()).as("excluded from open orders").isEqualTo(OrderStatus.CANCELLED);
                return List.of();
            }).when(fulfillmentService).allocateToOpenOrders(any(), any());

            service.cancel(7L);

            verify(fulfillmentService).allocateToOpenOrders(any(), any());
        }
    }

    @Nested
    @DisplayName("refusals touch nothing")
    class Refusals {

        @Test
        void unknownOrderIsNotFoundAndNothingIsLocked() {
            when(orderRepository.findItemIdById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Order 99 not found");
            verifyNoInteractions(itemRepository, movementRepository, fulfillmentService);
        }

        @Test
        void completedOrderIsRefusedWithoutReturningStock() {
            order.allocate(8);
            orderExists(List.of(InventoryMovement.allocation(item, 8, order, null, true, "a")));

            assertThatThrownBy(() -> service.cancel(7L)).isInstanceOf(InvalidOrderStateException.class);

            assertThat(item.getStockOnHand()).isZero();
            verify(movementRepository, never()).save(any());
            verifyNoInteractions(fulfillmentService);
        }

        @Test
        void alreadyCancelledOrderIsRefusedWithoutReturningStockTwice() {
            order.cancel(java.time.LocalDateTime.now());
            orderExists(List.of());

            assertThatThrownBy(() -> service.cancel(7L))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Order 7 is already cancelled");
            verify(movementRepository, never()).save(any());
        }
    }
}
