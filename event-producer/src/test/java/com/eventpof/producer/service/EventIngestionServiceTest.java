package com.eventpof.producer.service;

import com.eventpof.common.dto.EventRequest;
import com.eventpof.producer.domain.outbox.OutboxEvent;
import com.eventpof.producer.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private EventIngestionService ingestionService;

    @Test
    void shouldSaveEventToOutbox() {
        EventRequest request = EventRequest.builder()
                .eventKey("key-001")
                .eventType("ORDER_CREATED")
                .createdBy("user@example.com")
                .sourceSystem("order-service")
                .correlationId("corr-123")
                .data(Map.of("value", "test"))
                .build();

        OutboxEvent savedEvent = OutboxEvent.builder().id("outbox-id-1").eventKey("key-001").build();
        when(outboxEventRepository.existsByEventKey("key-001")).thenReturn(false);
        when(outboxEventRepository.save(any())).thenReturn(savedEvent);

        String id = ingestionService.ingest(request);

        assertThat(id).isEqualTo("outbox-id-1");
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventKey()).isEqualTo("key-001");
        assertThat(captor.getValue().getPayload().auditData().createdBy()).isEqualTo("user@example.com");
    }

    @Test
    void shouldReturnExistingOutboxIdForDuplicateKey() {
        EventRequest request = EventRequest.builder()
                .eventKey("dup-key")
                .eventType("TEST")
                .createdBy("user")
                .sourceSystem("src")
                .data(Map.of())
                .build();

        OutboxEvent existing = OutboxEvent.builder().id("existing-id").eventKey("dup-key").build();
        when(outboxEventRepository.existsByEventKey("dup-key")).thenReturn(true);
        when(outboxEventRepository.findByEventKey("dup-key")).thenReturn(Optional.of(existing));

        String id = ingestionService.ingest(request);

        assertThat(id).isEqualTo("existing-id");
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() {
        EventRequest request = EventRequest.builder()
                .eventKey("key-no-corr")
                .eventType("TEST")
                .createdBy("user")
                .sourceSystem("src")
                .data(Map.of())
                .build();

        OutboxEvent savedEvent = OutboxEvent.builder().id("id-no-corr").eventKey("key-no-corr").build();
        when(outboxEventRepository.existsByEventKey(any())).thenReturn(false);
        when(outboxEventRepository.save(any())).thenReturn(savedEvent);

        ingestionService.ingest(request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload().auditData().correlationId()).isNotBlank();
    }
}
