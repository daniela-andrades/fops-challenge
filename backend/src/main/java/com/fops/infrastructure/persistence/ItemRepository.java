package com.fops.infrastructure.persistence;

import com.fops.domain.model.Item;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Locks the item row for the transaction to serialize stock allocations for the same item
     * and prevent overselling under concurrent orders or deliveries.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);

    boolean existsBySkuIgnoreCase(String sku);

    long countByStockOnHand(Integer stockOnHand);

    @Query("select coalesce(sum(i.stockOnHand), 0) from Item i")
    long sumStockOnHand();
}
