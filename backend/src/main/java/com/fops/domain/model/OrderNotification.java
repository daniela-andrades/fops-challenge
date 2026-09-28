package com.fops.domain.model;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Outbox entry for the order completed email. It is created in the same transaction that completes the order,
 * so no completion is left without a notification even if sending fails or the process crashes.
 * The unique constraint on order_id guarantees a single email per order.
 */
@Entity
@Table(name = "order_email_notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_email_notification_order", columnNames = "order_id"))
public class OrderNotification {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastAttemptAt;

    /** When the retry scheduler may try again. */
    private LocalDateTime nextAttemptAt;

    private LocalDateTime sentAt;

    protected OrderNotification() {
    }

    public OrderNotification(Order order, LocalDateTime nextAttemptAt) {
        this.order = order;
        this.recipient = order.getUser().getEmail();
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.createdAt = LocalDateTime.now();
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markSent(LocalDateTime now) {
        this.attempts++;
        this.status = NotificationStatus.SENT;
        this.lastAttemptAt = now;
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * Records a failed attempt and schedules the next one with exponential backoff (base, 2x base, 4x base...).
     * Once maxAttempts is reached the notification becomes FAILED.
     */
    public void markAttemptFailed(String error, LocalDateTime now, int maxAttempts, Duration baseDelay) {
        this.attempts++;
        this.lastAttemptAt = now;
        this.lastError = truncate(error);
        if (attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
            this.nextAttemptAt = null;
        } else {
            this.status = NotificationStatus.PENDING;
            this.nextAttemptAt = now.plus(baseDelay.multipliedBy(1L << (attempts - 1)));
        }
    }

    /**
     * Manual retry: puts the notification back in the queue with a fresh retry cycle.
     */
    public void requeue(LocalDateTime now) {
        if (status == NotificationStatus.SENT) {
            throw new BusinessRuleException("The notification for order " + order.getId() + " has already been sent");
        }
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public boolean isPending() {
        return status == NotificationStatus.PENDING;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
