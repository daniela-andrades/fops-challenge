package com.fops.application.dashboard;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.enums.OrderStatus;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final OrderNotificationRepository orderNotificationRepository;

    public DashboardService(UserRepository userRepository,
                            ItemRepository itemRepository,
                            OrderRepository orderRepository,
                            OrderNotificationRepository orderNotificationRepository) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.orderNotificationRepository = orderNotificationRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        long pending = orderRepository.countByStatus(OrderStatus.PENDING);
        long partial = orderRepository.countByStatus(OrderStatus.PARTIALLY_FULFILLED);
        long completed = orderRepository.countByStatus(OrderStatus.COMPLETED);

        return new DashboardSummary(
                userRepository.count(),
                itemRepository.count(),
                itemRepository.sumStockOnHand(),
                itemRepository.countByStockOnHand(0),
                pending + partial + completed,
                pending,
                partial,
                completed,
                orderRepository.sumRemainingQuantityByStatusIn(
                        List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FULFILLED)),
                orderNotificationRepository.countByStatus(NotificationStatus.SENT),
                orderNotificationRepository.countByStatus(NotificationStatus.PENDING),
                orderNotificationRepository.countByStatus(NotificationStatus.FAILED));
    }

    /**
     * @param openDemand           units still to be served on open orders
     * @param notificationsPending completion emails waiting to be sent or retried
     * @param notificationsFailed  completion emails that exhausted their automatic retries
     */
    public record DashboardSummary(long totalUsers,
                                   long totalItems,
                                   long totalStockOnHand,
                                   long itemsOutOfStock,
                                   long totalOrders,
                                   long pendingOrders,
                                   long partiallyFulfilledOrders,
                                   long completedOrders,
                                   long openDemand,
                                   long notificationsSent,
                                   long notificationsPending,
                                   long notificationsFailed) {
    }
}
