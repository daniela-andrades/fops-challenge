package com.fops.application.notification;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.model.Order;
import com.fops.domain.model.OrderNotification;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Exact times of the retry schedule, with a fixed clock: 30 s, 60 s, 120 s... then FAILED at the limit.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceClockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private OrderNotificationRepository repository;
    @Mock
    private JavaMailSender mailSender;

    private NotificationService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, mailSender, "no-reply@test.local", 4, 30_000, CLOCK);
        order = order(42, user(1), item(1, 0), 2);
        order.allocate(2);
    }

    @Test
    void queuedEmailIsFirstDueExactlyOneBaseDelayAfterCompletion() {
        service.enqueueOrderCompleted(order);

        ArgumentCaptor<OrderNotification> captor = ArgumentCaptor.forClass(OrderNotification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void failuresBackOffExactlyAndTheLastAttemptMarksItFailed() {
        OrderNotification notification = new OrderNotification(order, NOW);
        when(repository.findByOrderIdForUpdate(42L)).thenReturn(Optional.of(notification));
        doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));

        service.deliver(42L);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        service.deliver(42L);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        service.deliver(42L);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(120));
        service.deliver(42L);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isEqualTo(4);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.getLastAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void successfulSendIsStampedWithTheClocksTime() {
        OrderNotification notification = new OrderNotification(order, NOW);
        when(repository.findByOrderIdForUpdate(42L)).thenReturn(Optional.of(notification));

        service.deliver(42L);

        assertThat(notification.getSentAt()).isEqualTo(NOW);
    }

    @Test
    void dueNotificationsAreAskedForAsOfTheClocksNowInABoundedPage() {
        when(repository.findDueOrderIds(NotificationStatus.PENDING, NOW, PageRequest.of(0, 50))).thenReturn(List.of(1L));

        assertThat(service.findDueOrderIds(50)).containsExactly(1L);
    }

    @Test
    void manualRequeueMakesItDueImmediately() {
        OrderNotification notification = new OrderNotification(order, NOW.plusHours(1));
        notification.markAttemptFailed("down", NOW, 1, java.time.Duration.ofSeconds(30));
        when(repository.findByOrderIdForUpdate(42L)).thenReturn(Optional.of(notification));

        service.requeue(42L);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void theClockIsTheOnlySourceOfTime() {
        Clock later = Clock.offset(CLOCK, java.time.Duration.ofDays(3));
        NotificationService shifted = new NotificationService(repository, mailSender, "x@test.local", 4, 30_000, later);

        shifted.enqueueOrderCompleted(order);

        ArgumentCaptor<OrderNotification> captor = ArgumentCaptor.forClass(OrderNotification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNextAttemptAt()).isEqualTo(LocalDateTime.ofInstant(Instant.now(later), ZoneOffset.UTC).plusSeconds(30));
    }
}
