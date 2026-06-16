package com.eventpof.consumer.domain.processed;

import com.eventpof.common.domain.EventPayload;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@MappedEntity("processed_events")
public class ProcessedEvent {

    @Id
    @GeneratedValue
    private ObjectId id;

    @MappedProperty("eventKey")
    private String eventKey;

    @MappedProperty("payload")
    private EventPayload payload;

    @MappedProperty("status")
    private ProcessedEventStatus status;

    @MappedProperty("attemptCount")
    private int attemptCount;

    @MappedProperty("processedAt")
    private Instant processedAt;

    @MappedProperty("errorMessage")
    private String errorMessage;

    public static ProcessedEvent success(EventPayload payload, int attempts) {
        return ProcessedEvent.builder()
                .eventKey(payload.eventKey())
                .payload(payload)
                .status(ProcessedEventStatus.SUCCESS)
                .attemptCount(attempts)
                .processedAt(Instant.now())
                .build();
    }

    public static ProcessedEvent deadLetter(EventPayload payload, int attempts, String error) {
        return ProcessedEvent.builder()
                .eventKey(payload.eventKey())
                .payload(payload)
                .status(ProcessedEventStatus.DEAD_LETTER)
                .attemptCount(attempts)
                .processedAt(Instant.now())
                .errorMessage(error)
                .build();
    }
}
