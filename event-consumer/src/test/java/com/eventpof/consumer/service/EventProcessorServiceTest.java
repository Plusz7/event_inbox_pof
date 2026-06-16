package com.eventpof.consumer.service;

import com.eventpof.common.domain.AuditData;
import com.eventpof.common.domain.EventPayload;
import com.eventpof.consumer.domain.processed.ProcessedEvent;
import com.eventpof.consumer.domain.processed.ProcessedEventRepository;
import com.eventpof.consumer.domain.processed.ProcessedEventStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Plain Mockito — no Micronaut context needed for pure service logic
@ExtendWith(MockitoExtension.class)
class EventProcessorServiceTest {

    @Mock
    ProcessedEventRepository repository;

    @InjectMocks
    EventProcessorService service;

    @Test
    void shouldProcessNewEventAndSaveAsSuccess() {
        EventPayload payload = buildPayload("key-1", "ORDER_CREATED");
        when(repository.existsByEventKey("key-1")).thenReturn(false);

        service.process(payload);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProcessedEventStatus.SUCCESS);
        assertThat(captor.getValue().getEventKey()).isEqualTo("key-1");
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void shouldSkipAlreadyProcessedEvent() {
        EventPayload payload = buildPayload("dup-key", "ORDER_CREATED");
        when(repository.existsByEventKey("dup-key")).thenReturn(true);

        service.process(payload);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldHandleUnknownEventTypeWithoutThrowing() {
        EventPayload payload = buildPayload("key-unknown", "SOME_FUTURE_TYPE");
        when(repository.existsByEventKey(any())).thenReturn(false);

        service.process(payload);

        // even unknown types are persisted as SUCCESS — the switch logs a warning
        verify(repository).save(any());
    }

    @Test
    void shouldSaveDeadLetterEventWithCorrectState() {
        EventPayload payload = buildPayload("dlt-key", "ORDER_CREATED");
        when(repository.existsByEventKey("dlt-key")).thenReturn(false);

        service.handleDeadLetter(payload, 3, "Kafka processing failed after 3 retries");

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(repository).save(captor.capture());
        ProcessedEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ProcessedEventStatus.DEAD_LETTER);
        assertThat(saved.getAttemptCount()).isEqualTo(3);
        assertThat(saved.getErrorMessage()).contains("3 retries");
    }

    @Test
    void shouldNotSaveDuplicateDeadLetterEvent() {
        EventPayload payload = buildPayload("existing-dlt", "ORDER_CREATED");
        when(repository.existsByEventKey("existing-dlt")).thenReturn(true);

        service.handleDeadLetter(payload, 3, "error");

        verify(repository, never()).save(any());
    }

    private EventPayload buildPayload(String key, String type) {
        return EventPayload.builder()
                .eventKey(key)
                .eventType(type)
                .auditData(AuditData.of("user", "corr-id", "src-system"))
                .build();
    }
}
