package com.eventpof.producer.api;

import com.eventpof.common.dto.EventRequest;
import com.eventpof.producer.service.EventIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventIngestionService ingestionService;

    @PostMapping
    public ResponseEntity<EventAcceptedResponse> publishEvent(@Valid @RequestBody EventRequest request) {
        log.info("Received event request: key={}, type={}", request.eventKey(), request.eventType());
        String inboxId = ingestionService.ingest(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new EventAcceptedResponse(inboxId, request.eventKey(), "Event accepted for processing"));
    }

    public record EventAcceptedResponse(String inboxId, String eventKey, String message) {}
}
