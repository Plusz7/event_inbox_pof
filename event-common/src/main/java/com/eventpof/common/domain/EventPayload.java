package com.eventpof.common.domain;

import lombok.Builder;

import java.util.Map;

@Builder
public record EventPayload(
        String eventKey,
        String eventType,
        AuditData auditData,
        Map<String, Object> data
) {}
