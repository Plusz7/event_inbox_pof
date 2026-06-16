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
    private Instant publishedAt;
    private String lastError;

    public static InboxEvent fromPayload(EventPayload payload) {
        return InboxEvent.builder()
                .eventKey(payload.eventKey())
                .payload(payload)
                .status(InboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }

    public void markPublished() {
        this.status = InboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        this.status = InboxEventStatus.FAILED;
    }

    public void resetToPending() {
        this.status = InboxEventStatus.PENDING;
    }
}
