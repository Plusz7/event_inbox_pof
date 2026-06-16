package com.eventpof.consumer.infrastructure.kafka;

import com.eventpof.common.domain.EventPayload;
import com.eventpof.consumer.service.EventProcessorService;
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.configuration.kafka.exceptions.KafkaListenerException;
import io.micronaut.configuration.kafka.exceptions.KafkaListenerExceptionHandler;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@KafkaListener(
        groupId = "${kafka.consumer.group-id}",
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.AUTO,
        errorStrategy = @ErrorStrategy(
                value = ErrorStrategyValue.RETRY_ON_ERROR,
                retryCount = 3,
                retryDelay = "2s"
        )
)
public class KafkaEventConsumer implements KafkaListenerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final EventProcessorService processorService;
    private final DltEventProducer dltEventProducer;

    public KafkaEventConsumer(EventProcessorService processorService, DltEventProducer dltEventProducer) {
        this.processorService = processorService;
        this.dltEventProducer = dltEventProducer;
    }

    @Topic("${kafka.topics.events}")
    public void consume(
            @KafkaKey String key,
            EventPayload payload,
            ConsumerRecord<String, EventPayload> record) {   // ConsumerRecord injected without annotation

        log.info("Received event: key={}, type={}, partition={}, offset={}",
                payload.eventKey(), payload.eventType(),
                record.partition(), record.offset());

        processorService.process(payload);
    }

    // Called by Micronaut Kafka after all retry attempts are exhausted
    @Override
    public void handle(KafkaListenerException exception) {
        ConsumerRecord<?, ?> record = exception.getConsumerRecord().orElse(null);

        if (record != null && record.value() instanceof EventPayload payload) {
            log.error("Retries exhausted, routing to DLT: key={}", payload.eventKey(), exception);
            dltEventProducer.sendToDlt(payload.eventKey(), payload);
            processorService.handleDeadLetter(payload, 3, exception.getMessage());
        } else {
            log.error("Unhandled Kafka exception without recoverable record", exception);
        }
    }
}
