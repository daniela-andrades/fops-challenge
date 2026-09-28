package com.fops.domain.model;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.fops.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderNotificationTest {

    private static final Duration BASE_DELAY = Duration.ofSeconds(30);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 12, 0);

    private final Order order = order(3, user(1), item(1, 0), 5);

    @Test
    void startsPendingForTheOrderOwner() {
        OrderNotification notification = new OrderNotification(order, NOW);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRecipient()).isEqualTo("user1@test.local");
        assertThat(notification.getAttempts()).isZero();
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void markSentRecordsTheAttemptAndClearsTheSchedule() {
        OrderNotification notification = new OrderNotification(order, NOW);
        notification.markAttemptFailed("boom", NOW, 5, BASE_DELAY);

        notification.markSent(NOW.plusMinutes(1));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getAttempts()).isEqualTo(2);
        assertThat(notification.getSentAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.getLastError()).isNull();
    }

    @Test
    void failedAttemptsBackOffExponentially() {
        OrderNotification notification = new OrderNotification(order, NOW);

        notification.markAttemptFailed("down", NOW, 5, BASE_DELAY);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));

        notification.markAttemptFailed("down", NOW, 5, BASE_DELAY);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));

        notification.markAttemptFailed("down", NOW, 5, BASE_DELAY);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getLastError()).isEqualTo("down");
    }

    @Test
    void becomesFailedWhenAttemptsAreExhausted() {
        OrderNotification notification = new OrderNotification(order, NOW);

        notification.markAttemptFailed("down", NOW, 2, BASE_DELAY);
        notification.markAttemptFailed("still down", NOW, 2, BASE_DELAY);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.getLastError()).isEqualTo("still down");
        assertThat(notification.isPending()).isFalse();
    }

    @Test
    void truncatesVeryLongErrors() {
        OrderNotification notification = new OrderNotification(order, NOW);

        notification.markAttemptFailed("x".repeat(5000), NOW, 5, BASE_DELAY);

        assertThat(notification.getLastError()).hasSize(1000);
    }

    @Test
    void requeueStartsAFreshRetryCycle() {
        OrderNotification notification = new OrderNotification(order, NOW);
        notification.markAttemptFailed("down", NOW, 1, BASE_DELAY);

        notification.requeue(NOW.plusHours(1));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isZero();
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void cannotRequeueAnAlreadySentNotification() {
        OrderNotification notification = new OrderNotification(order, NOW);
        notification.markSent(NOW);

        assertThatThrownBy(() -> notification.requeue(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been sent");
    }
}
