package com.fops.application.notification;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.model.Order;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The completion email never travels inside the business transaction: nothing is sent for work that
 * rolled back, and a slow SMTP server never delays the request that completed the order.
 */
class NotificationDeliveryGuaranteesIT extends IntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderNotificationRepository notificationRepository;

    @Test
    void completionThatRollsBackSendsNoEmail() {
        Long orderId = new TransactionTemplate(transactionManager).execute(tx -> {
            Order order = fixtures.order(fixtures.user(), fixtures.item(5), 5);
            assertThat(notificationRepository.findByOrderId(order.getId())).as("queued inside the transaction").isPresent();
            tx.setRollbackOnly();
            return order.getId();
        });

        assertThat(orderRepository.findById(orderId)).isEmpty();
        assertThat(notificationRepository.findByOrderId(orderId)).isEmpty();
        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verifyNoInteractions(mailSender));
    }

    @Test
    void slowSmtpDoesNotDelayTheRequestThatCompletesTheOrder() {
        doAnswer(invocation -> {
            Thread.sleep(3000);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
        var user = fixtures.user();
        var item = fixtures.item(5);

        long start = System.nanoTime();
        Order order = fixtures.order(user, item, 5);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("request time with a 3 s SMTP server").isLessThan(1000);
        await().atMost(Duration.ofSeconds(8)).until(() -> notificationRepository.findByOrderId(order.getId())
                .map(n -> n.getStatus() == NotificationStatus.SENT).orElse(false));
    }
}
