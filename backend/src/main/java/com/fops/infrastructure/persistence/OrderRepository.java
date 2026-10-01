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

    /** Reads only the item id, so the item can be locked before the order itself is loaded. */
    @Query("select o.item.id from Order o where o.id = :id")
    Optional<Long> findItemIdById(@Param("id") Long id);

    boolean existsByItemId(Long itemId);

    long countByStatus(OrderStatus status);

    /** Units still owed per item, over the given (open) statuses: one aggregate query for every item. */
    @Query("select o.item.id as itemId, sum(o.remainingQuantity) as demand from Order o "
            + "where o.status in :statuses group by o.item.id")
    List<ItemDemand> sumRemainingQuantityByItem(@Param("statuses") List<OrderStatus> statuses);

    @Query("select coalesce(sum(o.remainingQuantity), 0) from Order o where o.status in :statuses")
    long sumRemainingQuantityByStatusIn(@Param("statuses") List<OrderStatus> statuses);
}
