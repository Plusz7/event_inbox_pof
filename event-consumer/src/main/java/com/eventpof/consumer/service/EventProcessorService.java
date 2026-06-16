package com.eventpof.consumer.service;

import com.eventpof.common.domain.EventPayload;
import com.eventpof.consumer.domain.processed.ProcessedEvent;
import com.eventpof.consumer.domain.processed.ProcessedEventRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EventProcessorService {

    private static final Logger log = LoggerFactory.getLogger(EventProcessorService.class);

    private final ProcessedEventRepository processedEventRepository;

    public EventProcessorService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public void process(EventPayload payload) {
        if (processedEventRepository.existsByEventKey(payload.eventKey())) {
            log.warn("Skipping duplicate event: key={}", payload.eventKey());
            return;
        }

        log.info("Processing event: key={}, type={}", payload.eventKey(), payload.eventType());

        switch (payload.eventType()) {
            case "ORDER_CREATED" -> handleOrderCreated(payload);
            default -> log.warn("Unknown event type: {}", payload.eventType());
        }

        processedEventRepository.save(ProcessedEvent.success(payload, 1));
        log.info("Event processed successfully: key={}", payload.eventKey());
    }

    public void handleDeadLetter(EventPayload payload, int attempts, String error) {
        log.error("Event moved to dead letter: key={}, attempts={}, error={}", payload.eventKey(), attempts, error);
        if (!processedEventRepository.existsByEventKey(payload.eventKey())) {
            processedEventRepository.save(ProcessedEvent.deadLetter(payload, attempts, error));
        }
    }

    private void handleOrderCreated(EventPayload payload) {
        log.debug("Handling ORDER_CREATED: key={}", payload.eventKey());
    }
}
