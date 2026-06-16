package com.eventpof.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.Map;

@Builder
public record EventRequest(
        @NotBlank String eventKey,
        @NotBlank String eventType,
        @NotBlank String createdBy,
        @NotBlank String sourceSystem,
        String correlationId,
        @NotNull Map<String, Object> data
) {}
