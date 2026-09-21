package com.eventpof.producer.service;

import com.eventpof.common.domain.AuditData;
import com.eventpof.common.domain.EventPayload;
import com.eventpof.producer.domain.outbox.OutboxEvent;
import com.eventpof.producer.domain.outbox.OutboxEventRepository;
import com.eventpof.producer.domain.outbox.OutboxEventStatus;
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
class OutboxRelayServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private OutboxRelayService relayService;

    @Test
    void shouldPublishPendingEventsAndMarkAsPublished() {
        OutboxEvent event = buildPendingEvent("key-1");
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(event)
                .thenReturn(null);
        when(kafkaEventPublisher.publish(any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relayService.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(outboxEventRepository).save(event);
    }

    @Test
    void shouldScheduleRetryWithBackoffOnPublishError() {
        OutboxEvent event = buildPendingEvent("key-fail");
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(event)
                .thenReturn(null);
        CompletableFuture<SendResult<String, EventPayload>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaEventPublisher.publish(any())).thenReturn(failedFuture);

        relayService.relay();

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getNextRetryAt()).isAfter(Instant.now());
        verify(outboxEventRepository).save(event);
    }

    @Test
    void shouldLeaveEventFailedAfterMaxRetries() {
        OutboxEvent event = buildPendingEvent("key-max");
        // simulate 3 previous failures
        event.markFailed("err1");
        event.markFailed("err2");
        event.markFailed("err3");
        event.scheduleRetry(Instant.now());

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(event)
                .thenReturn(null);
        CompletableFuture<SendResult<String, EventPayload>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaEventPublisher.publish(any())).thenReturn(failedFuture);

        relayService.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(null);

        relayService.relay();

        verify(kafkaEventPublisher, never()).publish(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldResetStuckInProgressEvents() {
        when(mongoTemplate.updateMulti(any(), any(), eq(OutboxEvent.class)))
                .thenReturn(mock(com.mongodb.client.result.UpdateResult.class));

        relayService.resetStuckEvents();

        verify(mongoTemplate).updateMulti(any(), any(), eq(OutboxEvent.class));
    }

    private OutboxEvent buildPendingEvent(String key) {
        EventPayload payload = EventPayload.builder()
                .eventKey(key)
                .eventType("TEST")
                .auditData(AuditData.of("user", "corr", "src"))
                .build();
        Instant now = Instant.now();
        return OutboxEvent.builder()
                .id("id-" + key)
                .eventKey(key)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .nextRetryAt(now)
                .build();
    }
}
