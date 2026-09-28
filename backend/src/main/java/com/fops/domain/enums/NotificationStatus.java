package com.fops.domain.enums;

public enum NotificationStatus {
    /** Waiting to be sent or retried. */
    PENDING,
    SENT,
    /** Automatic retries exhausted; needs a manual retry. */
    FAILED
}
