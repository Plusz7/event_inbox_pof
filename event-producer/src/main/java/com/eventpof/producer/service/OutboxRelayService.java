package com.eventpof.producer.service;

import com.eventpof.producer.domain.outbox.OutboxEvent;
import com.eventpof.producer.domain.outbox.OutboxEventRepository;
import com.eventpof.producer.domain.outbox.OutboxEventStatus;
import com.eventpof.producer.infrastructure.kafka.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private static final int MAX_RETRY = 3;
    private static final int BATCH_SIZE = 10;
    private static final long PUBLISH_TIMEOUT_SECONDS = 5L;
    // exponential backoff between publish attempts: retry 1 → 30s, retry 2 → 120s
    private static final long BACKOFF_BASE_SECONDS = 30L;
    // events stuck in IN_PROGRESS longer than this are considered dead
    private static final long STUCK_THRESHOLD_SECONDS = 60L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:5000}")
    public void relay() {
        int processed = 0;
        OutboxEvent event;

        while (processed < BATCH_SIZE && (event = claimNextPending()) != null) {
            process(event);
            processed++;
        }

        if (processed > 0) {
            log.debug("Relay cycle finished, processed {} outbox events", processed);
        }
    }

    // Resets events stuck in IN_PROGRESS — e.g. app crashed mid-publish
    @Scheduled(fixedDelay = 600_000)
    public void resetStuckEvents() {
        Instant stuckThreshold = Instant.now().minusSeconds(STUCK_THRESHOLD_SECONDS);

        Update update = new Update()
                .set("status", OutboxEventStatus.PENDING)
                .set("nextRetryAt", Instant.now())
                .set("updatedAt", Instant.now())
                .inc("retryCount", 1)
                .set("lastError", "reset by stuck-event detector");

        var result = mongoTemplate.updateMulti(
                new Query(where("status").is(OutboxEventStatus.IN_PROGRESS)
                        .and("updatedAt").lte(stuckThreshold)),
                update,
                OutboxEvent.class
        );

        if (result.getModifiedCount() > 0) {
            log.warn("Stuck-event detector reset {} events from IN_PROGRESS to PENDING", result.getModifiedCount());
        }
    }

    // Atomically claims one PENDING event — only one cluster instance will receive any given event
    private OutboxEvent claimNextPending() {
        Query query = new Query(
                where("status").is(OutboxEventStatus.PENDING)
                        .and("nextRetryAt").lte(Instant.now())
        ).limit(1);

        Update update = new Update()
                .set("status", OutboxEventStatus.IN_PROGRESS)
                .set("updatedAt", Instant.now());

        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), OutboxEvent.class);
    }

    private void process(OutboxEvent event) {
        try {
            kafkaEventPublisher.publish(event.getPayload())
                    .orTimeout(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get();
            event.markPublished();
            log.info("Outbox event relayed: id={}, key={}", event.getId(), event.getEventKey());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Relay interrupted for event: id={}", event.getId(), ex);
            applyFailure(event, "Relay thread interrupted");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                log.error("Kafka publish timed out after {}s: id={}", PUBLISH_TIMEOUT_SECONDS, event.getId());
                applyFailure(event, "Publish timeout after " + PUBLISH_TIMEOUT_SECONDS + "s");
            } else {
                log.error("Failed to relay outbox event: id={}, attempt={}", event.getId(), event.getRetryCount(), ex);
                applyFailure(event, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            }
        }
        outboxEventRepository.save(event);
    }

    private void applyFailure(OutboxEvent event, String error) {
        event.markFailed(error);
        if (event.getRetryCount() < MAX_RETRY) {
            // 30s, then 120s — MAX_RETRY=3 means the 3rd failure fails permanently
            long backoffSeconds = BACKOFF_BASE_SECONDS * (long) Math.pow(4, event.getRetryCount() - 1);
            Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
            event.scheduleRetry(nextRetry);
            log.warn("Outbox event scheduled for retry: id={}, attempt={}, nextRetryAt={}",
                    event.getId(), event.getRetryCount(), nextRetry);
        } else {
            log.error("Outbox event permanently failed after {} attempts: id={}, lastError={}",
                    MAX_RETRY, event.getId(), error);
        }
    }
}
