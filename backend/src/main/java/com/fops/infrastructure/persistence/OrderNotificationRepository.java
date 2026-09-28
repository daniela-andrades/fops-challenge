package com.fops.infrastructure.persistence;

import com.fops.domain.enums.NotificationStatus;
import com.fops.domain.model.OrderNotification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    Optional<OrderNotification> findByOrderId(Long orderId);

    /**
     * Locks the notification while sending: if the immediate send and the retry scheduler
     * overlap, the second one waits and then sees SENT, so the email is never duplicated.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from OrderNotification n where n.order.id = :orderId")
    Optional<OrderNotification> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Query("select n.order.id from OrderNotification n where n.status = :status and n.nextAttemptAt <= :now order by n.nextAttemptAt asc")
    List<Long> findDueOrderIds(@Param("status") NotificationStatus status, @Param("now") LocalDateTime now, Pageable page);

    long countByStatus(NotificationStatus status);
}
