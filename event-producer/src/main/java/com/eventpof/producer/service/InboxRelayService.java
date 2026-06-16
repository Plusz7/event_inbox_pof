package com.eventpof.producer.service;

import com.eventpof.producer.domain.inbox.InboxEvent;
import com.eventpof.producer.domain.inbox.InboxEventRepository;
import com.eventpof.producer.domain.inbox.InboxEventStatus;
import com.eventpof.producer.infrastructure.kafka.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxRelayService {

    private static final int MAX_RETRY = 3;
    private static final int BATCH_SIZE = 10;
    private static final long PUBLISH_TIMEOUT_SECONDS = 5L;

    private final InboxEventRepository inboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelayString = "${inbox.relay.interval-ms:5000}")
    public void relay() {
        int processed = 0;
        InboxEvent event;

        while (processed < BATCH_SIZE && (event = claimNextPending()) != null) {
            process(event);
            processed++;
        }

        if (processed > 0) {
            log.debug("Relay cycle finished, processed {} inbox events", processed);
        }
    }

    /**
     * Atomically claims one PENDING event by flipping its status to IN_PROGRESS.
     * Only one instance across the cluster will receive any given event.
     */
    private InboxEvent claimNextPending() {
        Query query = new Query(where("status").is(InboxEventStatus.PENDING))
                .limit(1);
        query.fields().include("_id", "eventKey", "payload", "status", "retryCount", "createdAt", "lastError");

        Update update = new Update().set("status", InboxEventStatus.IN_PROGRESS);

        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                InboxEvent.class
        );
    }

    private void process(InboxEvent event) {
        try {
            kafkaEventPublisher.publish(event.getPayload())
                    .orTimeout(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get();
            event.markPublished();
            log.info("Inbox event relayed: id={}, key={}", event.getId(), event.getEventKey());
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                log.error("Kafka publish timed out after {}s: id={}", PUBLISH_TIMEOUT_SECONDS, event.getId());
                applyFailure(event, "Publish timeout after " + PUBLISH_TIMEOUT_SECONDS + "s");
            } else {
                log.error("Failed to relay inbox event: id={}, attempt={}", event.getId(), event.getRetryCount(), ex);
                applyFailure(event, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("Failed to relay inbox event: id={}, attempt={}", event.getId(), event.getRetryCount(), ex);
            applyFailure(event, ex.getMessage());
        }
        inboxEventRepository.save(event);
    }

    private void applyFailure(InboxEvent event, String error) {
        event.markFailed(error);
        if (event.getRetryCount() < MAX_RETRY) {
            event.resetToPending();
        }
    }
}
