package com.fops.application.order;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.fops.support.TestData.item;
import static com.fops.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    @Mock
    private OrderNotificationRepository orderNotificationRepository;
    @Mock
    private FulfillmentService fulfillmentService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void savesTheOrderThenFulfillsItFromTheLockedItem() {
        User user = user(1);
        Item item = item(2, 10);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(itemRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(item));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.createOrder(1L, 2L, 4);

        var inOrder = inOrder(orderRepository, fulfillmentService);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(fulfillmentService).fulfillFromStock(order, item);
        verify(itemRepository, never()).findById(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -2})
    void rejectsNonPositiveQuantityBeforeTouchingTheDatabase(int quantity) {
        assertThatThrownBy(() -> orderService.createOrder(1L, 2L, quantity)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(userRepository, itemRepository, orderRepository, fulfillmentService);
    }

    @Test
    void failsWhenUserOrItemDoNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.createOrder(1L, 2L, 3))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User 1 not found");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1)));
        when(itemRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.createOrder(1L, 2L, 3))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Item 2 not found");

        verifyNoInteractions(fulfillmentService);
    }
}
