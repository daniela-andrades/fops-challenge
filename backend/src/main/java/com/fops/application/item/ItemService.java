package com.fops.application.item;

import com.fops.application.inventory.InventoryService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.exception.ResourceInUseException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.Item;
import com.fops.domain.enums.OrderStatus;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemDemand;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryService inventoryService;

    public ItemService(ItemRepository itemRepository,
                       OrderRepository orderRepository,
                       InventoryMovementRepository movementRepository,
                       InventoryService inventoryService) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.movementRepository = movementRepository;
        this.inventoryService = inventoryService;
    }

    /**
     * Creates the item; initial stock is booked as an IN movement so every stock change is recorded.
     */
    @Transactional
    public Item createItem(String name, String sku, Integer initialStock) {
        String normalizedSku = validate(name, sku);
        if (initialStock != null && initialStock < 0) {
            throw new BusinessRuleException("Initial stock cannot be negative");
        }
        if (itemRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw duplicateSku(normalizedSku);
        }

        Item item = itemRepository.save(new Item(name.trim(), normalizedSku));
        if (initialStock != null && initialStock > 0) {
            inventoryService.registerIncomingInventory(item.getId(), initialStock, "Initial stock");
        }
        return item;
    }

    /**
     * Updates name and SKU. Stock is deliberately not editable: it only changes through inventory movements.
     */
    @Transactional
    public Item updateItem(Long id, String name, String sku) {
        String normalizedSku = validate(name, sku);
        Item item = findById(id);
        if (itemRepository.existsBySkuIgnoreCaseAndIdNot(normalizedSku, id)) {
            throw duplicateSku(normalizedSku);
        }

        item.setName(name.trim());
        item.setSku(normalizedSku);
        return item;
    }

    /**
     * Only items without orders or inventory history can be deleted, so no trace is ever lost.
     */
    @Transactional
    public void deleteItem(Long id) {
        Item item = findById(id);
        if (orderRepository.existsByItemId(id) || movementRepository.existsByItemId(id)) {
            throw new ResourceInUseException("Item " + item.getSku() + " has orders or inventory movements and cannot be deleted");
        }
        itemRepository.delete(item);
    }

    /**
     * Outstanding demand per item id (remaining quantity of its PENDING and PARTIALLY_FULFILLED orders).
     * Items without open orders are absent from the map. Read-only, a single aggregate query.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> findOutstandingDemandByItem() {
        return orderRepository.sumRemainingQuantityByItem(List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FULFILLED))
                .stream()
                .collect(Collectors.toMap(ItemDemand::getItemId, ItemDemand::getDemand));
    }

    @Transactional(readOnly = true)
    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Item findById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item " + id + " not found"));
    }

    private static String validate(String name, String sku) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Item name is required");
        }
        if (sku == null || sku.isBlank()) {
            throw new BusinessRuleException("SKU is required");
        }
        return sku.trim().toUpperCase();
    }

    private static DuplicateResourceException duplicateSku(String sku) {
        return new DuplicateResourceException("An item with SKU already exists: " + sku);
    }
}
