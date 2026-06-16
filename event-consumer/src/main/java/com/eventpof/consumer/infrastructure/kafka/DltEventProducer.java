package com.eventpof.consumer.infrastructure.kafka;

import com.eventpof.common.domain.EventPayload;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;

@KafkaClient
public interface DltEventProducer {

    @Topic("events.domain.DLT")
    void sendToDlt(@KafkaKey String key, EventPayload payload);
}
