package com.fops.domain.enums;

public enum OrderStatus {
    PENDING,
    PARTIALLY_FULFILLED,
    COMPLETED,
    /** Withdrawn before completion; every unit allocated to it was returned to stock. */
    CANCELLED
}
