package com.fops.api.controller;

import com.fops.api.dto.InventoryMovementDetailResponse;
import com.fops.api.dto.InventoryMovementRequest;
import com.fops.api.dto.InventoryMovementResponse;
import com.fops.api.dto.MovementUpdateRequest;
import com.fops.application.inventory.InventoryService;
import com.fops.domain.model.InventoryMovement;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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

    @PutMapping("/movements/{id}")
    public InventoryMovementResponse updateMovement(@PathVariable Long id, @Valid @RequestBody MovementUpdateRequest request) {
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        return InventoryMovementResponse.from(inventoryService.updateMovementReason(id, reason));
    }

    @DeleteMapping("/movements/{id}")
    public ResponseEntity<Void> deleteMovement(@PathVariable Long id) {
        inventoryService.deleteMovement(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Registers incoming stock. With an Idempotency-Key, a retried delivery returns the original movement with 200
     * instead of adding (and allocating) phantom stock; the same key with a different delivery is a 409.
     */
    @PostMapping("/incoming")
    public ResponseEntity<InventoryMovementResponse> registerIncoming(@Valid @RequestBody InventoryMovementRequest request,
                                                                      @RequestHeader(value = IdempotencyKey.HEADER, required = false) String idempotencyKey) {
        String requestId = IdempotencyKey.normalize(idempotencyKey);
        String reason = request.getReason() == null || request.getReason().isBlank() ? "Inventory restock" : request.getReason();
        InventoryMovement movement;
        try {
            movement = requestId == null
                    ? inventoryService.registerIncomingInventory(request.getItemId(), request.getQuantity(), reason)
                    : inventoryService.registerIncomingInventory(request.getItemId(), request.getQuantity(), reason, requestId);
        } catch (DataIntegrityViolationException duplicate) {
            // The failed transaction has rolled back; the original movement is read in a new transaction.
            InventoryMovement existing = requestId == null ? null : inventoryService.findByRequestId(requestId).orElse(null);
            if (existing == null) {
                throw duplicate;
            }
            IdempotencyKey.replayOf(requestId)
                    .compare("itemId", existing.getItem().getId(), request.getItemId())
                    .compare("quantity", existing.getQuantity(), request.getQuantity())
                    .requireSameRequest();
            return ResponseEntity.ok(InventoryMovementResponse.from(existing));
        }

        return ResponseEntity
                .created(URI.create("/api/inventory/movements/" + movement.getId()))
                .body(InventoryMovementResponse.from(movement));
    }
}
