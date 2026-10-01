package com.fops.application.dashboard;

import com.fops.application.dashboard.DashboardService.DashboardSummary;
import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.enums.OrderStatus;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderNotificationRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderNotificationRepository notificationRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void eachFigureComesFromItsOwnCount() {
        when(userRepository.count()).thenReturn(3L);
        when(itemRepository.count()).thenReturn(4L);
        when(itemRepository.sumStockOnHand()).thenReturn(65L);
        when(itemRepository.countByStockOnHand(0)).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(1L);
        when(orderRepository.countByStatus(OrderStatus.PARTIALLY_FULFILLED)).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.COMPLETED)).thenReturn(5L);
        when(orderRepository.sumRemainingQuantityByStatusIn(List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FULFILLED))).thenReturn(10L);
        when(notificationRepository.countByStatus(NotificationStatus.SENT)).thenReturn(4L);
        when(notificationRepository.countByStatus(NotificationStatus.PENDING)).thenReturn(1L);
        when(notificationRepository.countByStatus(NotificationStatus.FAILED)).thenReturn(0L);

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary).isEqualTo(new DashboardSummary(3, 4, 65, 2, 8, 1, 2, 5, 10, 4, 1, 0));
    }

    @Test
    void totalOrdersCountsPendingPartialAndCompletedButNotCancelled() {
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(1L);
        when(orderRepository.countByStatus(OrderStatus.PARTIALLY_FULFILLED)).thenReturn(1L);
        when(orderRepository.countByStatus(OrderStatus.COMPLETED)).thenReturn(1L);

        assertThat(dashboardService.getSummary().totalOrders()).isEqualTo(3);
    }
}
