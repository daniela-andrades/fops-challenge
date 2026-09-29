package com.fops.infrastructure.persistence;

import com.fops.domain.enums.OrderStatus;
import com.fops.domain.model.Item;
import com.fops.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findByItemAndStatusInOrderByCreatedAtAscIdAsc(Item item, List<OrderStatus> statuses);

    List<Order> findByItem(Item item);

    boolean existsByUserId(Long userId);

    Optional<Order> findByRequestId(String requestId);

    boolean existsByItemId(Long itemId);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.remainingQuantity), 0) from Order o where o.status in :statuses")
    long sumRemainingQuantityByStatusIn(@Param("statuses") List<OrderStatus> statuses);
}
