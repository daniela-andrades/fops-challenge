package com.fops.api.controller;

import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankHeaderMeansNoKey(String header) {
        assertThat(IdempotencyKey.normalize(header)).isNull();
    }

    @Test
    void keyIsTrimmedAndAcceptedUpToTheColumnLength() {
        assertThat(IdempotencyKey.normalize("  abc  ")).isEqualTo("abc");
        assertThat(IdempotencyKey.normalize("k".repeat(64))).hasSize(64);
    }

    @Test
    void keyLongerThanTheColumnIsRejected() {
        assertThatThrownBy(() -> IdempotencyKey.normalize("k".repeat(65)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Idempotency-Key must be at most 64 characters");
    }

    @Test
    void identicalRequestPassesTheComparison() {
        assertThatCode(() -> IdempotencyKey.replayOf("k")
                .compare("userId", 1L, 1L)
                .compare("requestedQuantity", 3, 3)
                .requireSameRequest()).doesNotThrowAnyException();
    }

    @Test
    void everyMismatchedFieldIsNamed() {
        assertThatThrownBy(() -> IdempotencyKey.replayOf("k")
                .compare("itemId", 2L, 2L)
                .compare("quantity", 5, 50)
                .compare("userId", 1L, null)
                .requireSameRequest())
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Idempotency-Key k was already used for a different request: "
                        + "quantity 5 stored, 50 requested; userId 1 stored, null requested");
    }
}
