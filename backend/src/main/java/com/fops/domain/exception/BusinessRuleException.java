package com.fops.domain.exception;

/**
 * A business rule violation (negative stock, over-allocation, invalid quantities...).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
