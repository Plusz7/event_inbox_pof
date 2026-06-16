package com.eventpof.consumer.infrastructure.kafka;

import com.eventpof.common.domain.AuditData;
import com.eventpof.common.domain.EventPayload;
import com.eventpof.consumer.domain.processed.ProcessedEventRepository;
import com.eventpof.consumer.domain.processed.ProcessedEventStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TestPropertyProvider is the correct Micronaut pattern for TestContainers:
 * getProperties() is called BEFORE the Micronaut context is created, and we
 * start containers explicitly there — no static-initializer race condition.
 */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "integration.tests", matches = "true")
class KafkaEventConsumerIntegrationTest implements TestPropertyProvider {

    // Declared without @Container — we control lifecycle via getProperties()
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1")
    ).withStartupTimeout(Duration.ofMinutes(2));

    static final MongoDBContainer mongo = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0")
    ).withStartupTimeout(Duration.ofMinutes(2));

    @Override
    public Map<String, String> getProperties() {
        // Start containers here — this runs before Micronaut context initialization
        if (!kafka.isRunning()) kafka.start();
        if (!mongo.isRunning()) mongo.start();

        return Map.of(
                "kafka.bootstrap.servers", kafka.getBootstrapServers(),
                "mongodb.uri", mongo.getConnectionString() + "/eventpof-test"
        );
    }

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    TestEventPublisher testEventPublisher;

    @BeforeEach
    void cleanUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldConsumeEventAndPersistAsProcessed() {
        String eventKey = UUID.randomUUID().toString();
        EventPayload payload = EventPayload.builder()
                .eventKey(eventKey)
                .eventType("ORDER_CREATED")
                .auditData(AuditData.of("test-user", "corr-1", "test"))
                .build();

        testEventPublisher.send(eventKey, payload);

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    assertThat(processedEventRepository.existsByEventKey(eventKey)).isTrue();
                    assertThat(processedEventRepository.findByEventKey(eventKey))
                            .isPresent()
                            .get()
                            .satisfies(e -> assertThat(e.getStatus()).isEqualTo(ProcessedEventStatus.SUCCESS));
                });
    }

    @Test
    void shouldSkipDuplicateEvents() {
        String eventKey = "dup-" + UUID.randomUUID();
        EventPayload payload = EventPayload.builder()
                .eventKey(eventKey)
                .eventType("ORDER_CREATED")
                .auditData(AuditData.of("user", "corr", "src"))
                .build();

        testEventPublisher.send(eventKey, payload);

        await()
                .atMost(Duration.ofSeconds(20))
                .until(() -> processedEventRepository.existsByEventKey(eventKey));

        // publish the same key again — consumer should silently skip it
        testEventPublisher.send(eventKey, payload);

        await()
                .pollDelay(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(processedEventRepository.count()).isEqualTo(1L)
                );
    }

    @Test
    void shouldRouteUnprocessableEventToDlt() {
        String eventKey = "dlt-" + UUID.randomUUID();
        // null eventType triggers the "unknown type" path but won't DLT unless processing throws
        // To properly test DLT we send a payload that will cause process() to throw
        EventPayload malformed = EventPayload.builder()
                .eventKey(eventKey)
                .eventType(null)   // will cause NPE in switch — triggers retry then DLT
                .auditData(AuditData.of("user", "corr", "src"))
                .build();

        testEventPublisher.send(eventKey, malformed);

        await()
                .atMost(Duration.ofSeconds(40))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() ->
                        assertThat(processedEventRepository.findByEventKey(eventKey))
                                .isPresent()
                                .get()
                                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(ProcessedEventStatus.DEAD_LETTER))
                );
    }
}
