package com.fops.infrastructure.persistence;

import com.fops.domain.model.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query("select m from InventoryMovement m order by m.createdAt asc, m.id asc")
    List<InventoryMovement> findAllChronological();

    @Query("select m from InventoryMovement m where m.item.id = :itemId order by m.createdAt asc, m.id asc")
    List<InventoryMovement> findByItemIdChronological(@Param("itemId") Long itemId);

    @Query("select m from InventoryMovement m where m.order.id = :orderId order by m.createdAt asc, m.id asc")
    List<InventoryMovement> findByOrderIdChronological(@Param("orderId") Long orderId);

    @Query("select m from InventoryMovement m where m.sourceMovement.id = :sourceId order by m.id asc")
    List<InventoryMovement> findBySourceMovementId(@Param("sourceId") Long sourceId);
}
