package com.fops.application.notification;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.Order;
import com.fops.domain.model.OrderNotification;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderNotificationRepository orderNotificationRepository;
    private final JavaMailSender mailSender;
    private final String from;
    private final int maxAttempts;
    private final Duration retryBaseDelay;
    private final Clock clock;

    public NotificationService(OrderNotificationRepository orderNotificationRepository,
                               JavaMailSender mailSender,
                               String from,
                               int maxAttempts,
                               long retryBaseDelayMs) {
        this(orderNotificationRepository, mailSender, from, maxAttempts, retryBaseDelayMs, Clock.systemDefaultZone());
    }

    /** The clock decides "now" for queueing, attempts and backoff, so tests can pin exact times. */
    @Autowired
    public NotificationService(OrderNotificationRepository orderNotificationRepository,
                               JavaMailSender mailSender,
                               @Value("${fops.mail.from}") String from,
                               @Value("${fops.notifications.max-attempts:5}") int maxAttempts,
                               @Value("${fops.notifications.retry-base-delay-ms:30000}") long retryBaseDelayMs,
                               Clock clock) {
        this.orderNotificationRepository = orderNotificationRepository;
        this.mailSender = mailSender;
        this.from = from;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelay = Duration.ofMillis(retryBaseDelayMs);
        this.clock = clock;
    }

    /**
     * Queues the email in the same transaction that completes the order. The listener makes the first attempt
     * after commit; if that never runs, the scheduler picks it up once the first retry interval elapses.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueOrderCompleted(Order order) {
        orderNotificationRepository.save(new OrderNotification(order, LocalDateTime.now(clock).plus(retryBaseDelay)));
    }

    /**
     * Attempts to send the order's email. Only acts on PENDING notifications, with the row locked,
     * so reprocessing the same event or overlapping with the scheduler never duplicates the email.
     *
     * @return true if the email was sent by this call
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(Long orderId) {
        OrderNotification notification = orderNotificationRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (notification == null) {
            log.warn("No queued notification for order {}", orderId);
            return false;
        }
        if (!notification.isPending()) {
            log.info("Notification for order {} is {}, skipping send", orderId, notification.getStatus());
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        try {
            mailSender.send(buildMessage(notification.getOrder(), notification.getRecipient()));
            notification.markSent(now);
            log.info("Order completed email sent to {} for order {}", notification.getRecipient(), orderId);
            return true;
        } catch (RuntimeException e) {
            notification.markAttemptFailed(describe(e), now, maxAttempts, retryBaseDelay);
            if (notification.getStatus() == NotificationStatus.FAILED) {
                log.error("Email for order {} gave up after {} attempts: {}", orderId, notification.getAttempts(), notification.getLastError());
            } else {
                log.warn("Failed to send email for order {} (attempt {}/{}), next attempt at {}: {}",
                        orderId, notification.getAttempts(), maxAttempts, notification.getNextAttemptAt(), notification.getLastError());
            }
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findDueOrderIds(int limit) {
        return orderNotificationRepository.findDueOrderIds(NotificationStatus.PENDING, LocalDateTime.now(clock), PageRequest.of(0, limit));
    }

    /**
     * Manual retry of a FAILED (or waiting PENDING) notification: requeues it with a fresh retry cycle.
     * Runs in its own transaction so the entity is not cached in the request's EntityManager (open-in-view),
     * which would make the response show the state from before the send.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requeue(Long orderId) {
        OrderNotification notification = orderNotificationRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " has no completion notification"));
        notification.requeue(LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public Optional<OrderNotification> findByOrderId(Long orderId) {
        return orderNotificationRepository.findByOrderId(orderId);
    }

    private SimpleMailMessage buildMessage(Order order, String recipient) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Order #" + order.getId() + " completed");
        message.setText("""
                Hi %s,

                Your order #%d has been completed.

                Product: %s (SKU %s)
                Requested quantity: %d
                Delivered quantity: %d
                Created at: %s
                Completed at: %s

                Fusion Operations
                """.formatted(
                order.getUser().getName(),
                order.getId(),
                order.getItem().getName(),
                order.getItem().getSku(),
                order.getRequestedQuantity(),
                order.getFulfilledQuantity(),
                format(order.getCreatedAt()),
                format(order.getCompletedAt())));
        return message;
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_FORMAT);
    }

    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return root == e ? message : e.getClass().getSimpleName() + ": " + message;
    }
}
