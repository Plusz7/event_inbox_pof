package com.eventpof.producer.api;

import com.eventpof.common.dto.EventRequest;
import com.eventpof.producer.domain.inbox.InboxEvent;
import com.eventpof.producer.domain.inbox.InboxEventStatus;
import com.eventpof.producer.service.EventIngestionService;
import com.eventpof.producer.service.EventStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventIngestionService ingestionService;
    private final EventStatusService statusService;

    @PostMapping
    public ResponseEntity<EventAcceptedResponse> publishEvent(@Valid @RequestBody EventRequest request) {
        log.info("Received event request: key={}, type={}", request.eventKey(), request.eventType());
        String inboxId = ingestionService.ingest(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new EventAcceptedResponse(inboxId, request.eventKey(), "Event accepted for processing"));
    }

    @GetMapping("/{inboxId}/status")
    public ResponseEntity<?> getEventStatus(@PathVariable String inboxId) {
        return statusService.findById(inboxId)
                .<ResponseEntity<?>>map(event -> ResponseEntity.ok(toStatusResponse(event)))
                .orElseGet(() -> {
                    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
                    problem.setTitle("Event not found");
                    problem.setDetail("No inbox event found for id: " + inboxId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
                });
    }

    private EventStatusResponse toStatusResponse(InboxEvent event) {
        return new EventStatusResponse(
                event.getId(),
                event.getEventKey(),
                event.getStatus(),
                event.getRetryCount(),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getLastError()
        );
    }

    public record EventAcceptedResponse(String inboxId, String eventKey, String message) {}

    public record EventStatusResponse(
            String inboxId,
            String eventKey,
            InboxEventStatus status,
            int retryCount,
            Instant createdAt,
            Instant publishedAt,
            String lastError
    ) {}
}
