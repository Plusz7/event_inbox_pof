package com.eventpof.producer.service;

import com.eventpof.common.domain.AuditData;
import com.eventpof.common.domain.EventPayload;
import com.eventpof.common.dto.EventRequest;
import com.eventpof.producer.domain.outbox.OutboxEvent;
import com.eventpof.producer.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final OutboxEventRepository outboxEventRepository;

    public String ingest(EventRequest request) {
        if (outboxEventRepository.existsByEventKey(request.eventKey())) {
            log.warn("Duplicate event key detected: {}", request.eventKey());
            return outboxEventRepository.findByEventKey(request.eventKey())
                    .map(OutboxEvent::getId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event not found for key: " + request.eventKey()));
        }

        String correlationId = request.correlationId() != null
                ? request.correlationId()
                : UUID.randomUUID().toString();

        AuditData auditData = AuditData.of(request.createdBy(), correlationId, request.sourceSystem());

        EventPayload payload = EventPayload.builder()
                .eventKey(request.eventKey())
                .eventType(request.eventType())
                .auditData(auditData)
                .data(request.data())
                .build();

        OutboxEvent outboxEvent = OutboxEvent.fromPayload(payload);
        OutboxEvent saved = outboxEventRepository.save(outboxEvent);

        log.info("Event saved to outbox: id={}, key={}", saved.getId(), saved.getEventKey());
        return saved.getId();
    }
}
