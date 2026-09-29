package com.fops.domain.exception;

/**
 * The operation is not allowed in the order's current state (for example, cancelling a completed order).
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
