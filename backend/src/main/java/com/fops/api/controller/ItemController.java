package com.fops.api.controller;

import com.fops.api.dto.ItemRequest;
import com.fops.api.dto.ItemResponse;
import com.fops.api.dto.ItemUpdateRequest;
import com.fops.application.item.ItemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<ItemResponse> getItems() {
        return itemService.findAll().stream()
                .map(ItemResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ItemResponse getItem(@PathVariable Long id) {
        return ItemResponse.from(itemService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemRequest request) {
        var item = itemService.createItem(request.getName(), request.getSku(), request.getStockOnHand());
        return ResponseEntity
                .created(URI.create("/api/items/" + item.getId()))
                .body(ItemResponse.from(item));
    }

    @PutMapping("/{id}")
    public ItemResponse updateItem(@PathVariable Long id, @Valid @RequestBody ItemUpdateRequest request) {
        return ItemResponse.from(itemService.updateItem(id, request.name(), request.sku()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        itemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
