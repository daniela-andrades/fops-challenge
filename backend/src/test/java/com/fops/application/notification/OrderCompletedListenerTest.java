package com.fops.application.notification;

import com.fops.domain.events.OrderCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCompletedListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderCompletedListener listener;

    @Test
    void triesToDeliverTheCompletedOrdersEmail() {
        listener.onOrderCompleted(new OrderCompletedEvent(5L));

        verify(notificationService).deliver(5L);
    }

    @Test
    void aFailingDeliveryNeverPropagatesBecauseTheOrderIsAlreadyCommitted() {
        when(notificationService.deliver(5L)).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> listener.onOrderCompleted(new OrderCompletedEvent(5L))).doesNotThrowAnyException();
    }
}
