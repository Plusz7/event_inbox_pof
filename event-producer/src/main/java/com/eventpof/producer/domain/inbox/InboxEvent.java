package com.eventpof.producer.domain.inbox;

import com.eventpof.common.domain.EventPayload;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@Document(collection = "inbox_events")
public class InboxEvent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventKey;

    private EventPayload payload;

    @Indexed
    private InboxEventStatus status;

    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;
    private Instant nextRetryAt;
    private String lastError;

    public static InboxEvent fromPayload(EventPayload payload) {
        Instant now = Instant.now();
        return InboxEvent.builder()
                .eventKey(payload.eventKey())
                .payload(payload)
                .status(InboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .nextRetryAt(now)
                .build();
    }

    public void markPublished() {
        this.status = InboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        this.status = InboxEventStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void scheduleRetry(Instant nextRetryAt) {
        this.status = InboxEventStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = Instant.now();
    }
}
