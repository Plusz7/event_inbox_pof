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

import java.time.Instant;
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
    // exponential backoff: retry 1 → 30s, retry 2 → 120s, retry 3 → 480s
    private static final long BACKOFF_BASE_SECONDS = 30L;
    // events stuck in IN_PROGRESS longer than this are considered dead
    private static final long STUCK_THRESHOLD_SECONDS = 60L;

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

    // Resets events stuck in IN_PROGRESS — e.g. app crashed mid-publish
    @Scheduled(fixedDelay = 600_000)
    public void resetStuckEvents() {
        Instant stuckThreshold = Instant.now().minusSeconds(STUCK_THRESHOLD_SECONDS);

        Update update = new Update()
                .set("status", InboxEventStatus.PENDING)
                .set("nextRetryAt", Instant.now())
                .set("updatedAt", Instant.now())
                .inc("retryCount", 1)
                .set("lastError", "reset by stuck-event detector");

        var result = mongoTemplate.updateMulti(
                new Query(where("status").is(InboxEventStatus.IN_PROGRESS)
                        .and("updatedAt").lte(stuckThreshold)),
                update,
                InboxEvent.class
        );

        if (result.getModifiedCount() > 0) {
            log.warn("Stuck-event detector reset {} events from IN_PROGRESS to PENDING", result.getModifiedCount());
        }
    }

    // Atomically claims one PENDING event — only one cluster instance will receive any given event
    private InboxEvent claimNextPending() {
        Query query = new Query(
                where("status").is(InboxEventStatus.PENDING)
                        .and("nextRetryAt").lte(Instant.now())
        ).limit(1);

        Update update = new Update()
                .set("status", InboxEventStatus.IN_PROGRESS)
                .set("updatedAt", Instant.now());

        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), InboxEvent.class);
    }

    private void process(InboxEvent event) {
        try {
            kafkaEventPublisher.publish(event.getPayload())
                    .orTimeout(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get();
            event.markPublished();
            log.info("Inbox event relayed: id={}, key={}", event.getId(), event.getEventKey());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Relay interrupted for event: id={}", event.getId(), ex);
            applyFailure(event, "Relay thread interrupted");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                log.error("Kafka publish timed out after {}s: id={}", PUBLISH_TIMEOUT_SECONDS, event.getId());
                applyFailure(event, "Publish timeout after " + PUBLISH_TIMEOUT_SECONDS + "s");
            } else {
                log.error("Failed to relay inbox event: id={}, attempt={}", event.getId(), event.getRetryCount(), ex);
                applyFailure(event, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            }
        }
        inboxEventRepository.save(event);
    }

    private void applyFailure(InboxEvent event, String error) {
        event.markFailed(error);
        if (event.getRetryCount() < MAX_RETRY) {
            // exponential backoff: 30s, 120s, 480s
            long backoffSeconds = BACKOFF_BASE_SECONDS * (long) Math.pow(4, event.getRetryCount() - 1);
            Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
            event.scheduleRetry(nextRetry);
            log.warn("Inbox event scheduled for retry: id={}, attempt={}, nextRetryAt={}",
                    event.getId(), event.getRetryCount(), nextRetry);
        } else {
            log.error("Inbox event permanently failed after {} attempts: id={}, lastError={}",
                    MAX_RETRY, event.getId(), error);
        }
    }
}
