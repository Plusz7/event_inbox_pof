package com.eventpof.producer.domain.outbox;

public enum OutboxEventStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    FAILED
}
