package com.fops.api.controller;

import com.fops.api.dto.InventoryMovementDetailResponse;
import com.fops.api.dto.InventoryMovementRequest;
import com.fops.api.dto.InventoryMovementResponse;
import com.fops.application.inventory.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/movements")
    public List<InventoryMovementResponse> getMovements(@RequestParam(required = false) Long itemId) {
        return inventoryService.findMovements(itemId).stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    @GetMapping("/movements/{id}")
    public InventoryMovementDetailResponse getMovement(@PathVariable Long id) {
        return InventoryMovementDetailResponse.from(
                inventoryService.findMovement(id),
                inventoryService.findAllocationsFrom(id));
    }

    @PostMapping("/incoming")
    public ResponseEntity<InventoryMovementResponse> registerIncoming(@Valid @RequestBody InventoryMovementRequest request) {
        var movement = inventoryService.registerIncomingInventory(
                request.getItemId(),
                request.getQuantity(),
                request.getReason() == null || request.getReason().isBlank() ? "Inventory restock" : request.getReason()
        );

        return ResponseEntity
                .created(URI.create("/api/inventory/movements/" + movement.getId()))
                .body(InventoryMovementResponse.from(movement));
    }
}
