package com.fops.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically retries pending emails whose next attempt is due.
 */
@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);
    private static final int BATCH_SIZE = 50;

    private final NotificationService notificationService;

    public NotificationRetryScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${fops.notifications.poll-interval-ms:15000}",
            initialDelayString = "${fops.notifications.poll-interval-ms:15000}")
    public void retryDueNotifications() {
        List<Long> due = notificationService.findDueOrderIds(BATCH_SIZE);
        if (due.isEmpty()) {
            return;
        }

        log.info("Retrying {} pending notification(s)", due.size());
        for (Long orderId : due) {
            try {
                notificationService.deliver(orderId);
            } catch (Exception e) {
                log.error("Unexpected error retrying the notification for order {}", orderId, e);
            }
        }
    }
}
