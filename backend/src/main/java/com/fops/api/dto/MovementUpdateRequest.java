package com.fops.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Editable movement fields: only the descriptive reason, the rest of the movement is immutable ledger data.
 */
public record MovementUpdateRequest(
        @Size(max = 255, message = "Reason cannot exceed 255 characters") String reason) {
}
