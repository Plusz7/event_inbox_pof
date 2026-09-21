package com.eventpof.consumer.domain.processed;

import com.eventpof.common.domain.AuditData;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * Persistence-side mirror of {@link AuditData}.
 * See {@link ProcessedPayload} for why the consumer keeps its own copy.
 */
@Serdeable
public record ProcessedAudit(
        String createdBy,
        Instant createdAt,
        String correlationId,
        String sourceSystem
) {
    public static ProcessedAudit from(AuditData auditData) {
        if (auditData == null) {
            return null;
        }
        return new ProcessedAudit(
                auditData.createdBy(),
                auditData.createdAt(),
                auditData.correlationId(),
                auditData.sourceSystem()
        );
    }
}
