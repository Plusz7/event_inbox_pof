# event-pof

Event-driven proof of concept using the **Inbox Pattern** over Apache Kafka.
Two independent services share a common domain library and communicate exclusively through Kafka topics.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           event-producer  (Spring Boot 3.4 · port 8080)    │
│                                                                             │
│  REST POST /api/v1/events                                                   │
│         │                                                                   │
│         ▼                                                                   │
│  ┌─────────────┐   idempotent save   ┌──────────────────┐                  │
│  │ Ingestion   │ ──────────────────► │  inbox_events    │  (MongoDB)        │
│  │ Service     │                     │  status=PENDING  │                   │
│  └─────────────┘                     └──────────────────┘                  │
│                                               │                             │
│                                  scheduler every 5 s                       │
│                                               │                             │
│  ┌─────────────┐   publish + timeout  ┌───────▼──────────┐                 │
│  │ InboxRelay  │ ────────────────────►│  Kafka Producer  │  3x retry       │
│  │ Service     │                      │  events.domain   │  + 5 s timeout  │
│  └─────────────┘                      └──────────────────┘                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                               │
                                   Kafka topic: events.domain
                                               │
┌──────────────────────────────────────────────▼──────────────────────────────┐
│                           event-consumer  (Micronaut 4.7 · port 8081)       │
│                                                                              │
│  ┌──────────────────┐   @KafkaListener   ┌──────────────────┐               │
│  │ KafkaEvent       │ ──────────────────►│ EventProcessor   │               │
│  │ Consumer         │  3x retry (2 s)    │ Service          │               │
│  │                  │  then → DLT        │                  │               │
│  └──────────────────┘                    └────────┬─────────┘               │
│           │                                       │ save                    │
│           │ on exhausted retries                  ▼                         │
│           ▼                             ┌──────────────────┐                │
│  ┌──────────────────┐                   │ processed_events │  (MongoDB)     │
│  │ DltEventProducer │                   │ status=SUCCESS   │                │
│  │ @KafkaClient     │                   │ status=DLT       │                │
│  └──────────────────┘                   └──────────────────┘                │
│           │                                                                  │
│           ▼                                                                  │
│    events.domain.DLT                                                         │
└──────────────────────────────────────────────────────────────────────────────┘

Observability layer (all ports in docker-compose):
  Loki ◄── logback appender (both services push logs)
  Prometheus ◄── /actuator/prometheus (producer) · /prometheus (consumer)
  Grafana ── datasources auto-provisioned: Loki + Prometheus
  Logstash ── TCP/Beats pipeline, stdout output (extend for Elasticsearch)
```

---

## Tech stack

| Layer | event-producer | event-consumer |
|---|---|---|
| Framework | Spring Boot **3.4.1** | Micronaut **4.7.3** |
| Java | 21 (LTS) | 21 (LTS) |
| Messaging | spring-kafka | micronaut-kafka |
| Persistence | Spring Data MongoDB | **Micronaut Data MongoDB** (compile-time queries) |
| Metrics | Micrometer + Prometheus | Micrometer + Prometheus |
| Logging | Loki4j + Logstash encoder | Loki4j + Logstash encoder |
| Tests | JUnit 5 + Mockito | JUnit 5 + Mockito + `@MicronautTest` |
| Integration tests | TestContainers (Kafka, Mongo) | TestContainers via **`TestPropertyProvider`** |

---

## Prerequisites

| Tool | Minimum version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9 | `mvn -version` |
| Docker | 24 | `docker version` |
| Docker Compose | v2 (plugin) | `docker compose version` |

---

## Quick start (local)

### 1 — Clone and build the common library

```bash
git clone <repo-url>
cd event-pof
mvn install -pl event-common
```

### 2 — Start the infrastructure

```bash
bash start-local.sh
```

This starts Kafka, Zookeeper, MongoDB, Loki, Promtail, Prometheus, Grafana, Logstash and creates the required Kafka topics.

Expected output:
```
Infrastructure ready. Start the apps:
  event-producer: cd event-producer && mvn spring-boot:run
  event-consumer: cd event-consumer && mvn spring-boot:run
```

### 3 — Start the services (two terminals)

```bash
# terminal 1
cd event-producer
mvn spring-boot:run

# terminal 2
cd event-consumer
mvn mn:run          # or: mvn micronaut:run
```

### 4 — Send a test event

```bash
curl -s -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventKey": "ord-001",
    "eventType": "ORDER_CREATED",
    "createdBy": "user@example.com",
    "sourceSystem": "order-service",
    "correlationId": "c-abc-123",
    "data": { "orderId": 42, "amount": 199.99 }
  }' | jq
```

Expected response (`202 Accepted`):
```json
{
  "inboxId": "665f1a2b3c4d5e6f7a8b9c0d",
  "eventKey": "ord-001",
  "message": "Event accepted for processing"
}
```

Within ~5 seconds the `InboxRelayService` picks up the event, publishes it to Kafka, and the consumer processes and persists it.

### 5 — Verify end-to-end

```bash
# Check inbox (producer side)
curl http://localhost:8082  # Mongo Express — browse inbox_events collection

# Check processed (consumer side)
# producer health
curl http://localhost:8080/actuator/health

# consumer health
curl http://localhost:8081/health
```

---

## Service URLs

| Service | URL | Credentials |
|---|---|---|
| event-producer API | http://localhost:8080/api/v1/events | — |
| event-producer health | http://localhost:8080/actuator/health | — |
| event-consumer health | http://localhost:8081/health | — |
| Kafka UI | http://localhost:9080 | — |
| Mongo Express | http://localhost:8082 | — |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | — |
| Loki | http://localhost:3100 | — |

---

## Module overview

### event-common

Pure Java library — no framework dependency. Contains shared domain objects consumed by both services.

```
event-common/src/main/java/com/eventpof/common/
├── domain/
│   ├── AuditData.java       record: createdBy, createdAt, correlationId, sourceSystem
│   └── EventPayload.java    record: eventKey, eventType, auditData, data (JsonNode)
└── dto/
    └── EventRequest.java    inbound REST request (validated)
```

**Why a shared library?** Both services must serialize/deserialize `EventPayload` through Kafka. Keeping the type in one place avoids schema drift.

### event-producer  _(Spring Boot 3.4)_

Accepts events via REST and guarantees at-least-once delivery to Kafka using the **Inbox Pattern**.

```
src/main/java/com/eventpof/producer/
├── api/
│   ├── EventController.java          POST /api/v1/events → 202 Accepted
│   └── GlobalExceptionHandler.java   RFC 9457 ProblemDetail responses
├── domain/inbox/
│   ├── InboxEvent.java               MongoDB document (status machine)
│   ├── InboxEventRepository.java     Spring Data interface
│   └── InboxEventStatus.java         PENDING → PUBLISHED | FAILED
├── service/
│   ├── EventIngestionService.java    validates + saves to inbox (idempotent on eventKey)
│   └── InboxRelayService.java        @Scheduled — reads PENDING, publishes, marks PUBLISHED
└── infrastructure/kafka/
    ├── KafkaEventPublisher.java       CompletableFuture + orTimeout(5s)
    └── KafkaProducerConfig.java       idempotent producer, acks=all, 3 broker-level retries
```

**Inbox pattern flow:**

```
POST /events
    → save InboxEvent(status=PENDING) to MongoDB   ← guaranteed durable write
    ← 202 Accepted

every 5 s:
    read top 10 PENDING
    for each:
        publish to Kafka (timeout 5 s)
        on success → mark PUBLISHED
        on failure → mark FAILED, retryCount++
        if retryCount < 3 → reset to PENDING (will retry next tick)
        if retryCount >= 3 → stays FAILED (manual intervention / alerting)
```

**Idempotency:** duplicate `eventKey` returns the existing `inboxId` without re-saving.

### event-consumer  _(Micronaut 4.7)_

Consumes `events.domain`, processes with 3-retry policy, routes failures to DLT.

```
src/main/java/com/eventpof/consumer/
├── ConsumerApplication.java                Micronaut.run(...)
├── domain/processed/
│   ├── ProcessedEvent.java                 @MappedEntity — Micronaut Data MongoDB entity
│   ├── ProcessedEventRepository.java       @MongoRepository — compile-time generated queries
│   └── ProcessedEventStatus.java           SUCCESS | FAILED | DEAD_LETTER
├── service/
│   └── EventProcessorService.java          @Singleton, idempotent on eventKey
└── infrastructure/kafka/
    ├── KafkaEventConsumer.java             @KafkaListener + @OffsetStrategy (type-level)
    │                                       implements KafkaListenerExceptionHandler → DLT
    └── DltEventProducer.java               @KafkaClient — publishes to events.domain.DLT
```

**Key Micronaut decisions:**

| Concern | Micronaut approach |
|---|---|
| DI | Constructor injection, `@Singleton` — no reflection at runtime |
| Repository | `@MongoRepository` — MongoDB queries generated at **compile time** by `micronaut-data-processor` |
| Kafka retry | `@ErrorStrategy(RETRY_ON_ERROR, retryCount=3, retryDelay="2s")` — declarative on `@KafkaListener` |
| DLT | `@KafkaClient` interface `DltEventProducer` — Micronaut generates the producer impl |
| Offset commit | `@OffsetStrategy(AUTO)` on the listener class — not on individual methods |
| Exception handling | Listener implements `KafkaListenerExceptionHandler.handle()` — called after retries exhausted |

**Micronaut Data MongoDB vs Spring Data:**
- Spring Data generates queries at runtime using reflection.
- Micronaut Data generates queries at **compile time** via annotation processing — zero reflection, faster startup, AOT-friendly, GraalVM-native ready.

---

## Retry and fault tolerance

### Producer side (Inbox → Kafka)

```
attempt 1 ──► Kafka (timeout 5 s)
    fail → attempt 2 (next scheduler tick, ~5 s later)
    fail → attempt 3
    fail → status=FAILED (no more auto-retries)
              └─► alert / manual replay
```

Kafka producer is configured with `retries=3, retry.backoff.ms=1000, acks=all, enable.idempotence=true`.

### Consumer side (Kafka → processing)

```
message received
    └─► process()
          fail → retry in 2 s
          fail → retry in 2 s
          fail → retry in 2 s
          fail (3rd) → KafkaListenerExceptionHandler.handle()
                          └─► DltEventProducer.sendToDlt()       (events.domain.DLT)
                          └─► processorService.handleDeadLetter() (MongoDB DEAD_LETTER)
```

Non-retryable exceptions (e.g., `IllegalArgumentException`) skip directly to the DLT handler.

---

## Testing

### Run all unit tests

```bash
mvn test
```

### Run integration tests (consumer module)

Requires Docker (TestContainers pulls images automatically).

```bash
cd event-consumer
mvn verify
```

Integration tests use `TestPropertyProvider` — the Micronaut-idiomatic way to inject TestContainers connection strings **before** the application context starts. No race condition with static initializers.

```java
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaEventConsumerIntegrationTest implements TestPropertyProvider {

    static final KafkaContainer kafka = new KafkaContainer(...);
    static final MongoDBContainer mongo = new MongoDBContainer(...);

    @Override
    public Map<String, String> getProperties() {
        // containers started explicitly here → context created after
        if (!kafka.isRunning()) kafka.start();
        if (!mongo.isRunning()) mongo.start();
        return Map.of(
            "kafka.bootstrap.servers", kafka.getBootstrapServers(),
            "mongodb.uri", mongo.getConnectionString() + "/eventpof-test"
        );
    }
}
```

### Test coverage summary

| Test class | Type | What it covers |
|---|---|---|
| `EventIngestionServiceTest` | Unit (Mockito) | Idempotency, auditData creation, correlationId generation |
| `InboxRelayServiceTest` | Unit (Mockito) | Publish success, failure, max-retry boundary, empty inbox |
| `EventProcessorServiceTest` | Unit (Mockito) | Process new event, skip duplicate, DLT save, DLT dedup |
| `KafkaEventConsumerIntegrationTest` | Integration (TestContainers) | E2E consume, duplicate skip, DLT routing |

---

## Observability

### Logs → Loki → Grafana

Both services push structured JSON logs (via `loki4j` appender) to `http://localhost:3100`.

In Grafana (`http://localhost:3000`, admin/admin):
1. Go to **Explore**
2. Select datasource **Loki**
3. Query: `{app="event-consumer"}` or `{app="event-producer"}`

All logs include the `service` label, `level`, and host.

### Metrics → Prometheus → Grafana

Producer exposes: `http://localhost:8080/actuator/prometheus`
Consumer exposes: `http://localhost:8081/prometheus`

Prometheus scrapes both every 15 s. In Grafana, select datasource **Prometheus** and explore `kafka_*`, `mongodb_*`, `jvm_*` metrics.

### Kafka topics

Open Kafka UI at `http://localhost:9080`:
- `events.domain` — main event topic (3 partitions)
- `events.domain.DLT` — dead letter topic

---

## Configuration reference

### event-producer (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `MONGODB_URI` | `mongodb://admin:password@localhost:27017/...` | MongoDB connection string |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `kafka.topics.events` | `events.domain` | Target topic |
| `inbox.relay.interval-ms` | `5000` | Relay scheduler interval |

### event-consumer (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `MONGODB_URI` | `mongodb://admin:password@localhost:27017/...` | MongoDB connection string |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `kafka.topics.events` | `events.domain` | Consumed topic |
| `kafka.consumer.group-id` | `event-consumer-group` | Consumer group |
| `LOKI_URL` | `http://localhost:3100` | Loki push endpoint |

---

## Project structure

```
event-pof/
├── pom.xml                        parent — Java 21, shared BOM management
├── docker-compose.yml             full local environment
├── start-local.sh                 infra startup + topic creation script
│
├── event-common/                  shared domain (no framework)
│   └── src/main/java/com/eventpof/common/
│
├── event-producer/                Spring Boot 3.4 — REST + Inbox
│   ├── pom.xml                    imports spring-boot-dependencies BOM
│   └── src/
│
├── event-consumer/                Micronaut 4.7 — Kafka consumer
│   ├── pom.xml                    imports micronaut-platform BOM
│   └── src/
│
└── infra/
    ├── mongo/init.js              collection + index initialization
    ├── loki/loki-config.yml
    ├── promtail/promtail-config.yml
    ├── prometheus/prometheus.yml  scrape configs for both services
    ├── logstash/pipeline/         logstash.conf — Beats/TCP input
    └── grafana/provisioning/      auto-provisioned datasources
```

---

## Known limitations (by design for PoC)

| Limitation | Production fix |
|---|---|
| Single inbox relay instance — no distributed lock | Use MongoDB `findOneAndUpdate` with `status: IN_PROGRESS` to claim a batch atomically |
| No MongoDB transactions in relay | Use replica set + multi-document transactions, or accept at-least-once with consumer idempotency (already in place) |
| Logstash outputs to stdout only | Add Elasticsearch output and configure index lifecycle |
| `events.domain.DLT` not monitored | Add Kafka consumer for DLT that triggers alerts (PagerDuty, Slack) |
| No schema registry | Add Confluent Schema Registry + Avro or Protobuf for `EventPayload` |
