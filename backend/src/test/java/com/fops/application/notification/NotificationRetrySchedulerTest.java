package com.fops.application.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationRetryScheduler scheduler;

    @Test
    void retriesEveryDueNotificationInOrder() {
        when(notificationService.findDueOrderIds(anyInt())).thenReturn(List.of(3L, 8L, 13L));

        scheduler.retryDueNotifications();

        InOrder inOrder = inOrder(notificationService);
        inOrder.verify(notificationService).deliver(3L);
        inOrder.verify(notificationService).deliver(8L);
        inOrder.verify(notificationService).deliver(13L);
    }

    @Test
    void oneFailingNotificationDoesNotStopTheBatch() {
        when(notificationService.findDueOrderIds(anyInt())).thenReturn(List.of(3L, 8L));
        when(notificationService.deliver(3L)).thenThrow(new IllegalStateException("lock timeout"));

        assertThatCode(scheduler::retryDueNotifications).doesNotThrowAnyException();

        verify(notificationService).deliver(8L);
    }

    @Test
    void doesNothingWhenNothingIsDue() {
        when(notificationService.findDueOrderIds(anyInt())).thenReturn(List.of());

        scheduler.retryDueNotifications();

        verify(notificationService, never()).deliver(anyLong());
    }

    @Test
    void asksForABoundedBatch() {
        when(notificationService.findDueOrderIds(anyInt())).thenReturn(List.of());

        scheduler.retryDueNotifications();

        verify(notificationService).findDueOrderIds(50);
    }
}
