package com.eventpof.consumer.domain.processed;

import com.eventpof.common.domain.EventPayload;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/**
 * Persistence-side mirror of {@link EventPayload}.
 * <p>
 * Micronaut Data MongoDB writes entities through micronaut-serde, which needs compile-time
 * {@code @Serdeable} metadata on every nested type. Annotating {@link EventPayload} directly
 * would push a Micronaut annotation into event-common, which is deliberately framework-neutral
 * and shared with the Spring Boot producer. Mirroring it here keeps that boundary intact and
 * lets the stored shape evolve independently of the Kafka wire contract.
 */
@Serdeable
public record ProcessedPayload(
        String eventKey,
        String eventType,
        ProcessedAudit auditData,
        Map<String, Object> data
) {
    public static ProcessedPayload from(EventPayload payload) {
        return new ProcessedPayload(
                payload.eventKey(),
                payload.eventType(),
                ProcessedAudit.from(payload.auditData()),
                payload.data()
        );
    }
}
