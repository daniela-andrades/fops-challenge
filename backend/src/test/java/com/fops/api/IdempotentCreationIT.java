package com.fops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fops.domain.enums.MovementType;
import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.model.Item;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Idempotency-Key on POST /api/orders and POST /api/inventory/incoming: the key lands in a unique request_id
 * column, so a retry is rejected by the database and answered with the original resource (200).
 */
@AutoConfigureMockMvc
class IdempotentCreationIT extends IntegrationTest {

    private static final String ORDERS = "/api/orders";
    private static final String INCOMING = "/api/inventory/incoming";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private OrderNotificationRepository notificationRepository;

    // --- orders

    @Test
    void sameKeyTwiceCreatesOneOrderOneAllocationSetAndOneEmail() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);
        String key = newKey();

        MockHttpServletResponse first = post(ORDERS, key, orderBody(user, item, 4));
        long orderId = idOf(first);
        await().atMost(Duration.ofSeconds(5)).until(() -> notificationRepository.findByOrderId(orderId)
                .map(n -> n.getStatus() == NotificationStatus.SENT).orElse(false));
        int allocationsAfterFirst = movementRepository.findByOrderIdChronological(orderId).size();

        MockHttpServletResponse retry = post(ORDERS, key, orderBody(user, item, 4));

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(idOf(retry)).isEqualTo(orderId);
        assertThat(orderRepository.findByItem(item)).hasSize(1);
        assertThat(movementRepository.findByOrderIdChronological(orderId)).hasSize(allocationsAfterFirst).hasSize(1);
        assertThat(stockOf(item)).isEqualTo(6);
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verify(mailSender, times(1)).send(any(SimpleMailMessage.class)));
    }

    @Test
    void twoDifferentKeysCreateTwoOrders() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);

        assertThat(post(ORDERS, newKey(), orderBody(user, item, 2)).getStatus()).isEqualTo(201);
        assertThat(post(ORDERS, newKey(), orderBody(user, item, 2)).getStatus()).isEqualTo(201);

        assertThat(orderRepository.findByItem(item)).hasSize(2);
        assertThat(stockOf(item)).isEqualTo(6);
    }

    @Test
    void withoutAKeyEveryRequestCreatesAnOrderAsBefore() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);

        assertThat(post(ORDERS, null, orderBody(user, item, 1)).getStatus()).isEqualTo(201);
        assertThat(post(ORDERS, null, orderBody(user, item, 1)).getStatus()).isEqualTo(201);
        assertThat(post(ORDERS, "   ", orderBody(user, item, 1)).getStatus()).as("blank key = no key").isEqualTo(201);

        assertThat(orderRepository.findByItem(item)).hasSize(3);
        assertThat(orderRepository.findByItem(item)).allSatisfy(o -> assertThat(o.getRequestId()).isNull());
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneOrder() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);
        String key = newKey();

        List<MockHttpServletResponse> responses = sendConcurrently(2, ORDERS, key, orderBody(user, item, 3));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 200);
        assertThat(idOf(responses.get(0))).isEqualTo(idOf(responses.get(1)));
        assertThat(orderRepository.findByItem(item)).hasSize(1);
        assertThat(stockOf(item)).isEqualTo(7);
    }

    @Test
    void sameKeyWithADifferentOrderIsAConflictNamingTheMismatch() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);
        String key = newKey();
        post(ORDERS, key, orderBody(user, item, 2));

        MockHttpServletResponse reused = post(ORDERS, key, orderBody(user, item, 5));

        assertThat(reused.getStatus()).isEqualTo(409);
        assertThat(json.readTree(reused.getContentAsString()).get("message").asText())
                .isEqualTo("Idempotency-Key " + key + " was already used for a different request: requestedQuantity 2 stored, 5 requested");
        assertThat(orderRepository.findByItem(item)).hasSize(1);
        assertThat(stockOf(item)).isEqualTo(8);
    }

    @Test
    void failedRequestLeavesTheKeyFreeForTheCorrectedRetry() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);
        String key = newKey();

        assertThat(post(ORDERS, key, "{\"userId\":%d,\"itemId\":999999,\"requestedQuantity\":1}".formatted(user.getId())).getStatus())
                .isEqualTo(404);
        assertThat(post(ORDERS, key, orderBody(user, item, 1)).getStatus()).isEqualTo(201);
    }

    @Test
    void rejectsKeysLongerThanTheColumn() throws Exception {
        User user = fixtures.user();
        Item item = fixtures.item(10);

        assertThat(post(ORDERS, "k".repeat(65), orderBody(user, item, 1)).getStatus()).isEqualTo(422);
        assertThat(orderRepository.findByItem(item)).isEmpty();
    }

    // --- incoming inventory

    @Test
    void retriedDeliveryAddsAndAllocatesStockOnce() throws Exception {
        Item item = fixtures.item(0);
        fixtures.order(fixtures.user(), item, 3);
        String key = newKey();
        String body = deliveryBody(item, 10);

        MockHttpServletResponse first = post(INCOMING, key, body);
        MockHttpServletResponse retry = post(INCOMING, key, body);

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(idOf(retry)).isEqualTo(idOf(first));
        assertThat(movementRepository.findByItemIdChronological(item.getId()))
                .extracting(m -> m.getMovementType())
                .containsExactly(MovementType.IN, MovementType.OUT);
        assertThat(stockOf(item)).isEqualTo(7);
    }

    @Test
    void concurrentDeliveriesWithTheSameKeyAddStockOnce() throws Exception {
        Item item = fixtures.item(0);
        String key = newKey();

        List<MockHttpServletResponse> responses = sendConcurrently(2, INCOMING, key, deliveryBody(item, 5));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 200);
        assertThat(movementRepository.findByItemIdChronological(item.getId())).hasSize(1);
        assertThat(stockOf(item)).isEqualTo(5);
    }

    @Test
    void sameKeyWithADifferentDeliveryIsAConflict() throws Exception {
        Item item = fixtures.item(0);
        String key = newKey();
        post(INCOMING, key, deliveryBody(item, 5));

        MockHttpServletResponse reused = post(INCOMING, key, deliveryBody(item, 50));

        assertThat(reused.getStatus()).isEqualTo(409);
        assertThat(json.readTree(reused.getContentAsString()).get("message").asText())
                .endsWith("quantity 5 stored, 50 requested");
        assertThat(stockOf(item)).isEqualTo(5);
    }

    @Test
    void deliveriesWithoutAKeyBehaveAsBefore() throws Exception {
        Item item = fixtures.item(0);

        post(INCOMING, null, deliveryBody(item, 5));
        post(INCOMING, null, deliveryBody(item, 5));

        assertThat(movementRepository.findByItemIdChronological(item.getId())).hasSize(2);
        assertThat(stockOf(item)).isEqualTo(10);
    }

    // --- helpers

    private List<MockHttpServletResponse> sendConcurrently(int threads, String path, String key, String body) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return post(path, key, body);
            }));
        }
        start.countDown();
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (Future<MockHttpServletResponse> future : futures) {
            responses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return responses;
    }

    private MockHttpServletResponse post(String path, String key, String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private long idOf(MockHttpServletResponse response) throws Exception {
        JsonNode node = json.readTree(response.getContentAsString());
        return node.get("id").asLong();
    }

    private int stockOf(Item item) {
        return itemRepository.findById(item.getId()).orElseThrow().getStockOnHand();
    }

    private static String orderBody(User user, Item item, int quantity) {
        return "{\"userId\":%d,\"itemId\":%d,\"requestedQuantity\":%d}".formatted(user.getId(), item.getId(), quantity);
    }

    private static String deliveryBody(Item item, int quantity) {
        return "{\"itemId\":%d,\"quantity\":%d,\"reason\":\"Delivery note\"}".formatted(item.getId(), quantity);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
