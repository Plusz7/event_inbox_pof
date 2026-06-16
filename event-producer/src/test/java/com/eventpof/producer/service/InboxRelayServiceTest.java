package com.eventpof.producer.service;

import com.eventpof.common.domain.AuditData;
import com.eventpof.common.domain.EventPayload;
import com.eventpof.producer.domain.inbox.InboxEvent;
import com.eventpof.producer.domain.inbox.InboxEventRepository;
import com.eventpof.producer.domain.inbox.InboxEventStatus;
import com.eventpof.producer.infrastructure.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxRelayServiceTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private InboxRelayService relayService;

    @Test
    void shouldPublishPendingEventsAndMarkAsPublished() {
        InboxEvent event = buildPendingEvent("key-1");
        // first call returns the event (claimed), second call returns null (no more pending)
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(InboxEvent.class)))
                .thenReturn(event)
                .thenReturn(null);
        when(kafkaEventPublisher.publish(any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relayService.relay();

        assertThat(event.getStatus()).isEqualTo(InboxEventStatus.PUBLISHED);
        verify(inboxEventRepository).save(event);
    }

    @Test
    void shouldMarkEventAsFailedOnPublishError() {
        InboxEvent event = buildPendingEvent("key-fail");
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(InboxEvent.class)))
                .thenReturn(event)
                .thenReturn(null);
        CompletableFuture<SendResult<String, EventPayload>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaEventPublisher.publish(any())).thenReturn(failedFuture);

        relayService.relay();

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(InboxEventStatus.PENDING);
        verify(inboxEventRepository).save(event);
    }

    @Test
    void shouldLeaveEventFailedAfterMaxRetries() {
        InboxEvent event = buildPendingEvent("key-max");
        event.markFailed("err");
        event.markFailed("err");
        event.markFailed("err");
        event.resetToPending();

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(InboxEvent.class)))
                .thenReturn(event)
                .thenReturn(null);
        CompletableFuture<SendResult<String, EventPayload>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaEventPublisher.publish(any())).thenReturn(failedFuture);

        relayService.relay();

        assertThat(event.getStatus()).isEqualTo(InboxEventStatus.FAILED);
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(InboxEvent.class)))
                .thenReturn(null);

        relayService.relay();

        verify(kafkaEventPublisher, never()).publish(any());
        verify(inboxEventRepository, never()).save(any());
    }

    private InboxEvent buildPendingEvent(String key) {
        EventPayload payload = EventPayload.builder()
                .eventKey(key)
                .eventType("TEST")
                .auditData(AuditData.of("user", "corr", "src"))
                .build();
        return InboxEvent.builder()
                .id("id-" + key)
                .eventKey(key)
                .payload(payload)
                .status(InboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }
}
