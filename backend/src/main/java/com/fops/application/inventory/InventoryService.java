package com.fops.application.inventory;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.ResourceInUseException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.enums.MovementType;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private final ItemRepository itemRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final FulfillmentService fulfillmentService;

    public InventoryService(ItemRepository itemRepository,
                            InventoryMovementRepository inventoryMovementRepository,
                            FulfillmentService fulfillmentService) {
        this.itemRepository = itemRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * Records incoming stock and immediately allocates it to the item's open orders, oldest first.
     */
    @Transactional
    public InventoryMovement registerIncomingInventory(Long itemId, Integer quantity, String reason) {
        return registerIncomingInventory(itemId, quantity, reason, null);
    }

    /**
     * Same as {@link #registerIncomingInventory(Long, Integer, String)}, tagged with the client's Idempotency-Key.
     * The movement row is inserted before its stock is allocated, so a duplicate key fails with nothing allocated.
     */
    @Transactional
    public InventoryMovement registerIncomingInventory(Long itemId, Integer quantity, String reason, String requestId) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("Inventory quantity must be greater than 0");
        }

        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item " + itemId + " not found"));

        item.increaseStock(quantity);
        InventoryMovement newMovement = InventoryMovement.incoming(item, quantity, reason);
        newMovement.assignRequestId(requestId);
        InventoryMovement incoming = inventoryMovementRepository.save(newMovement);

        fulfillmentService.allocateToOpenOrders(item, incoming);
        return incoming;
    }

    /**
     * Only the reason can change: quantity, item and links are the ledger and stay immutable.
     */
    @Transactional
    public InventoryMovement updateMovementReason(Long id, String reason) {
        InventoryMovement movement = findMovement(id);
        movement.changeReason(reason);
        return movement;
    }

    /**
     * Removes an incoming movement registered by mistake. Allowed only while none of its units were allocated
     * to orders and they are all still on hand, so the ledger stays exact and no trace is lost.
     * OUT movements belong to their order and are never deleted on their own.
     */
    @Transactional
    public void deleteMovement(Long id) {
        InventoryMovement movement = findMovement(id);
        if (movement.getMovementType() == MovementType.OUT) {
            throw new ResourceInUseException("Movement " + id + " is an allocation to order "
                    + movement.getOrder().getId() + " and can only change through that order");
        }
        if (inventoryMovementRepository.existsBySourceMovementId(id)) {
            throw new ResourceInUseException("Movement " + id + " was already allocated to orders and cannot be deleted");
        }

        Item item = itemRepository.findByIdForUpdate(movement.getItem().getId()).orElseThrow();
        if (item.getStockOnHand() < movement.getQuantity()) {
            throw new BusinessRuleException("Movement " + id + " cannot be deleted: only " + item.getStockOnHand()
                    + " of its " + movement.getQuantity() + " units are still in stock");
        }
        item.decreaseStock(movement.getQuantity());
        inventoryMovementRepository.delete(movement);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryMovement> findByRequestId(String requestId) {
        return inventoryMovementRepository.findByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> findMovements(Long itemId) {
        return itemId == null
                ? inventoryMovementRepository.findAllChronological()
                : inventoryMovementRepository.findByItemIdChronological(itemId);
    }

    @Transactional(readOnly = true)
    public InventoryMovement findMovement(Long id) {
        return inventoryMovementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movement " + id + " not found"));
    }

    /**
     * OUT movements fed by an IN movement: answers "which orders did this stock go to".
     */
    @Transactional(readOnly = true)
    public List<InventoryMovement> findAllocationsFrom(Long movementId) {
        return inventoryMovementRepository.findBySourceMovementId(movementId);
    }
}
