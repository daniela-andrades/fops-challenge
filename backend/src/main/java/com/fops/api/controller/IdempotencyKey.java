package com.fops.api.controller;

import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Optional Idempotency-Key header of the creating endpoints that move stock. The key is stored in a unique
 * request_id column; a retry hits that constraint and gets the original resource back instead of a duplicate.
 */
final class IdempotencyKey {

    static final String HEADER = "Idempotency-Key";
    private static final int MAX_LENGTH = 64;

    private final String key;
    private final List<String> mismatches = new ArrayList<>();

    private IdempotencyKey(String key) {
        this.key = key;
    }

    /** Blank means no key; a key longer than the request_id column is rejected. */
    static String normalize(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String key = header.trim();
        if (key.length() > MAX_LENGTH) {
            throw new BusinessRuleException(HEADER + " must be at most " + MAX_LENGTH + " characters");
        }
        return key;
    }

    /** Starts comparing a retried request against the resource stored under the same key. */
    static IdempotencyKey replayOf(String key) {
        return new IdempotencyKey(key);
    }

    IdempotencyKey compare(String field, Object stored, Object requested) {
        if (!Objects.equals(stored, requested)) {
            mismatches.add(field + " " + stored + " stored, " + requested + " requested");
        }
        return this;
    }

    /** A reused key must describe the same request; returning another request's resource would be wrong. */
    void requireSameRequest() {
        if (!mismatches.isEmpty()) {
            throw new DuplicateResourceException(HEADER + " " + key + " was already used for a different request: "
                    + String.join("; ", mismatches));
        }
    }
}
