package com.eventpof.producer.service;

import com.eventpof.common.dto.EventRequest;
import com.eventpof.producer.domain.inbox.InboxEvent;
import com.eventpof.producer.domain.inbox.InboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @InjectMocks
    private EventIngestionService ingestionService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldSaveEventToInbox() {
        ObjectNode data = mapper.createObjectNode().put("value", "test");
        EventRequest request = EventRequest.builder()
                .eventKey("key-001")
                .eventType("ORDER_CREATED")
                .createdBy("user@example.com")
                .sourceSystem("order-service")
                .correlationId("corr-123")
                .data(data)
                .build();

        InboxEvent savedEvent = InboxEvent.builder().id("inbox-id-1").eventKey("key-001").build();
        when(inboxEventRepository.existsByEventKey("key-001")).thenReturn(false);
        when(inboxEventRepository.save(any())).thenReturn(savedEvent);

        String id = ingestionService.ingest(request);

        assertThat(id).isEqualTo("inbox-id-1");
        ArgumentCaptor<InboxEvent> captor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(inboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventKey()).isEqualTo("key-001");
        assertThat(captor.getValue().getPayload().auditData().createdBy()).isEqualTo("user@example.com");
    }

    @Test
    void shouldReturnExistingInboxIdForDuplicateKey() {
        ObjectNode data = mapper.createObjectNode();
        EventRequest request = EventRequest.builder()
                .eventKey("dup-key")
                .eventType("TEST")
                .createdBy("user")
                .sourceSystem("src")
                .data(data)
                .build();

        InboxEvent existing = InboxEvent.builder().id("existing-id").eventKey("dup-key").build();
        when(inboxEventRepository.existsByEventKey("dup-key")).thenReturn(true);
        when(inboxEventRepository.findByEventKey("dup-key")).thenReturn(Optional.of(existing));

        String id = ingestionService.ingest(request);

        assertThat(id).isEqualTo("existing-id");
        verify(inboxEventRepository, never()).save(any());
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() {
        ObjectNode data = mapper.createObjectNode();
        EventRequest request = EventRequest.builder()
                .eventKey("key-no-corr")
                .eventType("TEST")
                .createdBy("user")
                .sourceSystem("src")
                .data(data)
                .build();

        InboxEvent savedEvent = InboxEvent.builder().id("id-no-corr").eventKey("key-no-corr").build();
        when(inboxEventRepository.existsByEventKey(any())).thenReturn(false);
        when(inboxEventRepository.save(any())).thenReturn(savedEvent);

        ingestionService.ingest(request);

        ArgumentCaptor<InboxEvent> captor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(inboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload().auditData().correlationId()).isNotBlank();
    }
}
