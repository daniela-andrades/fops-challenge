package com.fops.application.notification;

import com.fops.domain.events.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * First delivery attempt, in the background and only after the business transaction commits:
 * a slow or failing SMTP server never delays or rolls back fulfillment. Failures are retried by NotificationRetryScheduler.
 */
@Component
public class OrderCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedListener.class);

    private final NotificationService notificationService;

    public OrderCompletedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        try {
            notificationService.deliver(event.orderId());
        } catch (Exception e) {
            log.error("Unexpected error notifying order {}; the retry scheduler will pick it up", event.orderId(), e);
        }
    }
}
