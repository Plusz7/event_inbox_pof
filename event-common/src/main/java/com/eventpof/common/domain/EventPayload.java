package com.eventpof.common.domain;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder
public record EventPayload(
        String eventKey,
        String eventType,
        AuditData auditData,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
        JsonNode data
) {}
