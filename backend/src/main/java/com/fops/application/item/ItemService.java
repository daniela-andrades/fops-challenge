package com.fops.application.item;

import com.fops.application.inventory.InventoryService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.Item;
import com.fops.infrastructure.persistence.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final InventoryService inventoryService;

    public ItemService(ItemRepository itemRepository, InventoryService inventoryService) {
        this.itemRepository = itemRepository;
        this.inventoryService = inventoryService;
    }

    /**
     * Creates the item; initial stock is booked as an IN movement so every stock change is recorded.
     */
    @Transactional
    public Item createItem(String name, String sku, Integer initialStock) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Item name is required");
        }
        if (sku == null || sku.isBlank()) {
            throw new BusinessRuleException("SKU is required");
        }
        if (initialStock != null && initialStock < 0) {
            throw new BusinessRuleException("Initial stock cannot be negative");
        }

        String normalizedSku = sku.trim().toUpperCase();
        if (itemRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new DuplicateResourceException("An item with SKU already exists: " + normalizedSku);
        }

        Item item = itemRepository.save(new Item(name.trim(), normalizedSku));
        if (initialStock != null && initialStock > 0) {
            inventoryService.registerIncomingInventory(item.getId(), initialStock, "Initial stock");
        }
        return item;
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
}
