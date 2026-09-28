package com.fops.application.notification;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.Order;
import com.fops.domain.model.OrderNotification;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_DELAY_MS = 30_000;

    @Mock
    private OrderNotificationRepository repository;
    @Mock
    private JavaMailSender mailSender;

    private NotificationService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, mailSender, "no-reply@test.local", MAX_ATTEMPTS, BASE_DELAY_MS);
        order = order(42, user(1), item(1, 0), 5);
        order.allocate(5);
    }

    @Test
    void enqueuesAPendingNotificationScheduledAfterTheFirstRetryDelay() {
        service.enqueueOrderCompleted(order);

        ArgumentCaptor<OrderNotification> captor = ArgumentCaptor.forClass(OrderNotification.class);
        verify(repository).save(captor.capture());
        OrderNotification queued = captor.getValue();
        assertThat(queued.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(queued.getRecipient()).isEqualTo("user1@test.local");
        assertThat(queued.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(25));
    }

    @Test
    void sendsTheEmailAndMarksItSent() {
        OrderNotification notification = pending();

        assertThat(service.deliver(42L)).isTrue();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@test.local");
        assertThat(message.getTo()).containsExactly("user1@test.local");
        assertThat(message.getSubject()).isEqualTo("Order #42 completed");
        assertThat(message.getText())
                .contains("Hi User 1", "Your order #42 has been completed", "Item 1 (SKU SKU-1)",
                        "Requested quantity: 5", "Delivered quantity: 5")
                .containsPattern("Completed at: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getAttempts()).isEqualTo(1);
    }

    @Test
    void doesNotResendAnAlreadySentNotification() {
        OrderNotification notification = pending();
        notification.markSent(LocalDateTime.now());

        assertThat(service.deliver(42L)).isFalse();
        verifyNoInteractions(mailSender);
    }

    @Test
    void returnsFalseWhenNothingIsQueued() {
        when(repository.findByOrderIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThat(service.deliver(42L)).isFalse();
        verifyNoInteractions(mailSender);
    }

    @Test
    void keepsTheNotificationPendingAndSchedulesARetryWhenSmtpFails() {
        OrderNotification notification = pending();
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(service.deliver(42L)).isFalse();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getLastError()).contains("Connection refused");
        assertThat(notification.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(25));
    }

    @Test
    void marksTheNotificationFailedAfterTheLastAttempt() {
        OrderNotification notification = pending();
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            service.deliver(42L);
        }

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(service.deliver(42L)).isFalse();
        verify(mailSender, times(MAX_ATTEMPTS)).send(any(SimpleMailMessage.class));
    }

    @Test
    void requeueRejectsOrdersWithoutNotification() {
        when(repository.findByOrderIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requeue(42L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private OrderNotification pending() {
        OrderNotification notification = new OrderNotification(order, LocalDateTime.now());
        lenient().when(repository.findByOrderIdForUpdate(42L)).thenReturn(Optional.of(notification));
        return notification;
    }
}
