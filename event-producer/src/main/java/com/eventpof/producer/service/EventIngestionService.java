package com.eventpof.producer.service;

import com.eventpof.common.domain.AuditData;
import com.eventpof.common.domain.EventPayload;
import com.eventpof.common.dto.EventRequest;
import com.eventpof.producer.domain.inbox.InboxEvent;
import com.eventpof.producer.domain.inbox.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final InboxEventRepository inboxEventRepository;

    public String ingest(EventRequest request) {
        if (inboxEventRepository.existsByEventKey(request.eventKey())) {
            log.warn("Duplicate event key detected: {}", request.eventKey());
            return inboxEventRepository.findByEventKey(request.eventKey())
                    .map(InboxEvent::getId)
                    .orElseThrow(() -> new IllegalStateException("Inbox event not found for key: " + request.eventKey()));
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

        InboxEvent inboxEvent = InboxEvent.fromPayload(payload);
        InboxEvent saved = inboxEventRepository.save(inboxEvent);

        log.info("Event saved to inbox: id={}, key={}", saved.getId(), saved.getEventKey());
        return saved.getId();
    }
}
