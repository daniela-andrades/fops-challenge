package com.fops.application.notification;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import com.fops.domain.model.OrderNotification;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Case 4 of the spec plus delivery guarantees: queued with the completion, sent in the background,
 * retried on failure and never duplicated.
 */
class OrderCompletionNotificationIT extends IntegrationTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private OrderNotificationRepository notificationRepository;
    @Autowired
    private NotificationRetryScheduler retryScheduler;

    @Test
    @DisplayName("Case 4: completing an order emails its creator exactly once")
    void completingAnOrderEmailsItsCreatorOnce() {
        User user = fixtures.user();
        Order order = fixtures.order(user, fixtures.item(50), 10);

        awaitStatus(order, NotificationStatus.SENT);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly(user.getEmail());
        assertThat(captor.getValue().getSubject()).isEqualTo("Order #" + order.getId() + " completed");

        assertThat(notificationService.deliver(order.getId())).isFalse();
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void partialFulfillmentDoesNotNotify() {
        Item item = fixtures.item(0);
        Order order = fixtures.order(fixtures.user(), item, 8);

        fixtures.incoming(item, 5);

        assertThat(notificationRepository.findByOrderId(order.getId())).isEmpty();
        verifyNoInteractions(mailSender);
    }

    @Test
    void incomingStockThatCompletesAnOrderTriggersTheEmail() {
        Item item = fixtures.item(0);
        Order order = fixtures.order(fixtures.user(), item, 8);

        fixtures.incoming(item, 10);

        awaitStatus(order, NotificationStatus.SENT);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void failedEmailStaysQueuedAndTheSchedulerDeliversItOnceDue() {
        doThrow(new MailSendException("SMTP down")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        Order order = fixtures.order(fixtures.user(), fixtures.item(5), 5);

        OrderNotification afterFailure = awaitAttempts(order, 1);
        assertThat(afterFailure.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(afterFailure.getLastError()).contains("SMTP down");

        retryScheduler.retryDueNotifications();
        assertThat(reload(order).getAttempts()).as("not due yet").isEqualTo(1);

        makeDue(order);
        retryScheduler.retryDueNotifications();

        assertThat(reload(order).getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void exhaustedEmailIsFailedUntilRequeuedManually() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        Order order = fixtures.order(fixtures.user(), fixtures.item(3), 3);
        awaitAttempts(order, 1);

        notificationService.deliver(order.getId());
        notificationService.deliver(order.getId());
        assertThat(reload(order).getStatus()).isEqualTo(NotificationStatus.FAILED);

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        notificationService.requeue(order.getId());
        assertThat(notificationService.deliver(order.getId())).isTrue();
        assertThat(reload(order).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    private void makeDue(Order order) {
        OrderNotification notification = reload(order);
        org.springframework.test.util.ReflectionTestUtils.setField(notification, "nextAttemptAt", LocalDateTime.now().minusSeconds(1));
        notificationRepository.save(notification);
    }

    private OrderNotification reload(Order order) {
        return notificationRepository.findByOrderId(order.getId()).orElseThrow();
    }

    private void awaitStatus(Order order, NotificationStatus status) {
        await().atMost(ASYNC_TIMEOUT).until(() -> notificationRepository.findByOrderId(order.getId())
                .map(n -> n.getStatus() == status).orElse(false));
    }

    private OrderNotification awaitAttempts(Order order, int attempts) {
        await().atMost(ASYNC_TIMEOUT).until(() -> notificationRepository.findByOrderId(order.getId())
                .map(n -> n.getAttempts() >= attempts).orElse(false));
        return reload(order);
    }
}
