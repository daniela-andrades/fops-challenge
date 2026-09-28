package com.fops.application.inventory;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("Inventory quantity must be greater than 0");
        }

        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item " + itemId + " not found"));

        item.increaseStock(quantity);
        InventoryMovement incoming = inventoryMovementRepository.save(InventoryMovement.incoming(item, quantity, reason));

        fulfillmentService.allocateToOpenOrders(item, incoming);
        return incoming;
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
