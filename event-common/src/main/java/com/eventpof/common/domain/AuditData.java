package com.eventpof.common.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.Instant;

@Builder
public record AuditData(
        String createdBy,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt,
        String correlationId,
        String sourceSystem
) {
    public static AuditData of(String createdBy, String correlationId, String sourceSystem) {
        return AuditData.builder()
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .correlationId(correlationId)
                .sourceSystem(sourceSystem)
                .build();
    }
}
