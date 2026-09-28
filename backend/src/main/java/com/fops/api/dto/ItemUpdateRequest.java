package com.fops.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Editable item fields. Stock is intentionally absent: it only changes through inventory movements.
 */
public record ItemUpdateRequest(
        @NotBlank(message = "Item name is required") @Size(max = 255) String name,
        @NotBlank(message = "SKU is required") @Size(max = 64) String sku) {
}
