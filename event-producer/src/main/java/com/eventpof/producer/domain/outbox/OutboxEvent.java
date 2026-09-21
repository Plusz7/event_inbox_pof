package com.eventpof.producer.domain.outbox;

import com.eventpof.common.domain.EventPayload;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@Document(collection = "outbox_events")
public class OutboxEvent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventKey;

    private EventPayload payload;

    @Indexed
    private OutboxEventStatus status;

    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;
    private Instant nextRetryAt;
    private String lastError;

    public static OutboxEvent fromPayload(EventPayload payload) {
        Instant now = Instant.now();
        return OutboxEvent.builder()
                .eventKey(payload.eventKey())
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .nextRetryAt(now)
                .build();
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        this.status = OutboxEventStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void scheduleRetry(Instant nextRetryAt) {
        this.status = OutboxEventStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = Instant.now();
    }
}
