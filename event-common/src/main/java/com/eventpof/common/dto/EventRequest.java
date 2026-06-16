package com.eventpof.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record EventRequest(
        @NotBlank String eventKey,
        @NotBlank String eventType,
        @NotBlank String createdBy,
        @NotBlank String sourceSystem,
        String correlationId,
        @NotNull JsonNode data
) {}
