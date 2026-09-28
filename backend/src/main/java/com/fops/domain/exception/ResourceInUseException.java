package com.fops.domain.exception;

/**
 * The resource cannot be removed because other records depend on it (orders, inventory history).
 */
public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
