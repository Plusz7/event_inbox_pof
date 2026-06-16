package com.eventpof.producer.infrastructure.kafka;

import com.eventpof.common.domain.EventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, EventPayload> kafkaTemplate;

    @Value("${kafka.topics.events}")
    private String eventsTopic;

    public CompletableFuture<SendResult<String, EventPayload>> publish(EventPayload payload) {
        log.debug("Publishing event to Kafka: key={}, topic={}", payload.eventKey(), eventsTopic);
        return kafkaTemplate.send(eventsTopic, payload.eventKey(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event: key={}", payload.eventKey(), ex);
                    } else {
                        log.info("Event published: key={}, offset={}", payload.eventKey(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
