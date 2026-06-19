# event-pof

Event-driven proof of concept using the **Inbox Pattern** over Apache Kafka.
Two independent services share a common domain library and communicate exclusively through Kafka topics.

### Stack

| | |
|---|---|
| **Languages** | Java 21 (preview features), Maven 3.9 |
| **event-producer** | Spring Boot 4.1 · Spring Data MongoDB · Spring Kafka · Micrometer |
| **event-consumer** | Micronaut 4.7 · Micronaut Data MongoDB · Micronaut Kafka · Micrometer |
| **Messaging** | Apache Kafka 7.6 (Confluent) · Zookeeper |
| **Persistence** | MongoDB 7.0 |
| **Observability** | Prometheus · Grafana 10.4 · Loki 2.9 · Promtail · Logstash 8.14 |
| **Logging** | Loki4j appender · Logstash JSON encoder |
| **Testing** | JUnit 5 · Mockito · TestContainers |
| **Packaging** | Docker · Docker Compose · Spring Boot fat JAR · maven-shade-plugin |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           event-producer  (Spring Boot 4.1 · port 8080)    │
│                                                                             │
│  REST POST /api/v1/events                                                   │
│  REST GET  /api/v1/events/{inboxId}/status                                  │
│         │                                                                   │
│         ▼                                                                   │
│  ┌─────────────┐   idempotent save   ┌──────────────────┐                  │
│  │ Ingestion   │ ──────────────────► │  inbox_events    │  (MongoDB)        │
│  │ Service     │                     │  status=PENDING  │                   │
│  └─────────────┘                     └──────────────────┘                  │
│                                               │                             │
│                                  findAndModify (atomic claim)               │
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
  Grafana ── datasources auto-provisioned: Loki + Prometheus · dashboard: EventPOF Overview
  Logstash ── TCP/Beats pipeline, stdout output (extend for Elasticsearch)
```

---

## Tech stack

| Layer | event-producer | event-consumer |
|---|---|---|
| Framework | Spring Boot **4.1.0** | Micronaut **4.7.x** |
| Java | 21 (preview features enabled) | 21 (preview features enabled) |
| Messaging | spring-kafka | micronaut-kafka |
| Persistence | Spring Data MongoDB | Micronaut Data MongoDB (compile-time queries) |
| Metrics | Micrometer + Prometheus | Micrometer + Prometheus |
| Logging | Loki4j + Logstash encoder | Loki4j + Logstash encoder |
| Tests | JUnit 5 + Mockito | JUnit 5 + Mockito + `@MicronautTest` |
| Integration tests | — | TestContainers via `TestPropertyProvider` |
| Packaging | Spring Boot fat JAR | maven-shade-plugin fat JAR |

---

## Prerequisites

| Tool | Minimum version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9 | `mvn -version` |
| Docker | 24 | `docker version` |
| Docker Compose | v2 (plugin) | `docker compose version` |

---

## Quick start (Docker — recommended)

### 1 — Build

```bash
git clone <repo-url>
cd event-pof
mvn clean install -DskipTests
```

### 2 — Start everything

```bash
docker compose up -d --build
```

This starts all infrastructure (Kafka, MongoDB, Loki, Prometheus, Grafana, Logstash) **and** both application services.

### 3 — Send a test event

```bash
# Linux / macOS
curl -s -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventKey": "ord-001",
    "eventType": "ORDER_CREATED",
    "createdBy": "user@example.com",
    "sourceSystem": "order-service",
    "correlationId": "c-abc-123",
    "data": { "orderId": 42, "amount": 199.99 }
  }'
```

```powershell
# Windows PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/events" -Method POST `
  -ContentType "application/json" -UseBasicParsing `
  -Body '{"eventKey":"ord-001","eventType":"ORDER_CREATED","createdBy":"user@example.com","sourceSystem":"order-service","data":{"orderId":42}}'
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

### 4 — Check event status

```bash
curl http://localhost:8080/api/v1/events/{inboxId}/status
```

### 5 — Verify end-to-end

```bash
# producer health
curl http://localhost:8080/actuator/health

# consumer health
curl http://localhost:8081/health

# browse MongoDB collections
open http://localhost:8082   # Mongo Express
```

---

## Service URLs

| Service | URL | Credentials |
|---|---|---|
| event-producer API | http://localhost:8080/api/v1/events | — |
| event-producer health | http://localhost:8080/actuator/health | — |
| event-producer metrics | http://localhost:8080/actuator/prometheus | — |
| event-consumer health | http://localhost:8081/health | — |
| event-consumer metrics | http://localhost:8081/prometheus | — |
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
│   └── EventPayload.java    record: eventKey, eventType, auditData, data (Map<String,Object>)
└── dto/
    └── EventRequest.java    inbound REST request (validated)
```

**Why a shared library?** Both services must serialize/deserialize `EventPayload` through Kafka. Keeping the type in one place avoids schema drift.

### event-producer  _(Spring Boot 4.1)_

Accepts events via REST and guarantees at-least-once delivery to Kafka using the **Inbox Pattern**.

```
src/main/java/com/eventpof/producer/
├── api/
│   ├── EventController.java          POST /api/v1/events → 202 Accepted
│   │                                 GET  /api/v1/events/{inboxId}/status → 200 | 404
│   └── GlobalExceptionHandler.java   RFC 9457 ProblemDetail responses
├── domain/inbox/
│   ├── InboxEvent.java               MongoDB document (status machine)
│   ├── InboxEventRepository.java     Spring Data interface
│   └── InboxEventStatus.java         PENDING → IN_PROGRESS → PUBLISHED | FAILED
├── service/
│   ├── EventIngestionService.java    validates + saves to inbox (idempotent on eventKey)
│   ├── EventStatusService.java       reads inbox event by id
│   └── InboxRelayService.java        @Scheduled — atomic claim via findAndModify
└── infrastructure/kafka/
    ├── KafkaEventPublisher.java       CompletableFuture + orTimeout(5s)
    └── KafkaProducerConfig.java       idempotent producer, acks=all, 3 retries
```

**Inbox pattern flow (atomic claim — race-condition safe):**

```
POST /events
    → save InboxEvent(status=PENDING) to MongoDB   ← guaranteed durable write
    ← 202 Accepted

every 5 s:
    findAndModify(status=PENDING → IN_PROGRESS)    ← atomic — safe for multiple instances
    for each claimed event:
        publish to Kafka (timeout 5 s)
        on success → mark PUBLISHED
        on failure → mark FAILED, retryCount++
```

**Idempotency:** duplicate `eventKey` returns the existing `inboxId` without re-saving.

### event-consumer  _(Micronaut 4.7)_

Consumes `events.domain`, processes with 3-retry policy, routes failures to DLT.

```
src/main/java/com/eventpof/consumer/
├── ConsumerApplication.java
├── domain/processed/
│   ├── ProcessedEvent.java                 @MappedEntity — Micronaut Data MongoDB entity
│   ├── ProcessedEventRepository.java       @MongoRepository — compile-time generated queries
│   └── ProcessedEventStatus.java           SUCCESS | FAILED | DEAD_LETTER
├── service/
│   └── EventProcessorService.java          @Singleton, idempotent on eventKey
└── infrastructure/kafka/
    ├── KafkaEventConsumer.java             @KafkaListener, implements KafkaListenerExceptionHandler
    └── DltEventProducer.java               @KafkaClient — publishes to events.domain.DLT
```

---

## Retry and fault tolerance

### Producer side (Inbox → Kafka)

Dwie niezależne warstwy retry — każda obsługuje inny rodzaj błędu:

**Warstwa 1 — Kafka producer retry** (ms–sekundy, chwilowe błędy sieci):
```
Kafka producer: retries=3, retry.backoff.ms=1000, acks=all, enable.idempotence=true
```

**Warstwa 2 — Inbox exponential backoff** (sekundy–minuty, długie awarie):
```
attempt 1 → fail → PENDING, nextRetryAt: now + 30s
attempt 2 → fail → PENDING, nextRetryAt: now + 120s
attempt 3 → fail → PENDING, nextRetryAt: now + 480s
attempt 4 → fail → FAILED (permanentnie)
```

`claimNextPending()` uwzględnia `nextRetryAt` — event nie jest podjęty przed upływem backoffu:
```java
where("status").is(PENDING).and("nextRetryAt").lte(Instant.now())
```

**Warstwa 3 — Stuck event detector** (co 10 min, ochrona przed crashem):

Jeśli aplikacja crashuje w trakcie `process()`, event zostaje w `IN_PROGRESS` na zawsze.
Osobny scheduler resetuje takie eventy z powrotem do `PENDING`:
```
find { status: IN_PROGRESS, updatedAt < 60s ago }
→ reset to PENDING, retryCount++
```

`InboxEvent` status machine:
```
PENDING ──► IN_PROGRESS ──► PUBLISHED
                │
                └──► PENDING (retry < 4, z backoffem)
                └──► FAILED  (retry = 4, permanentnie)
```

### Consumer side (Kafka → processing)

```
message received
    └─► process()
          fail → retry in 2 s  (×3)
          fail (3rd) → KafkaListenerExceptionHandler
                          └─► DltEventProducer → events.domain.DLT
                          └─► MongoDB: status=DEAD_LETTER
```

---

## Testing

### Run all unit tests

```bash
mvn test
```

### Run integration tests (requires Docker)

```bash
mvn test -Dintegration.tests=true -pl event-consumer
```

Integration tests use `TestPropertyProvider` — Micronaut-idiomatic pattern that starts TestContainers **before** the application context is created.

### Test coverage summary

| Test class | Type | What it covers |
|---|---|---|
| `EventIngestionServiceTest` | Unit (Mockito) | Idempotency, auditData creation, correlationId generation |
| `InboxRelayServiceTest` | Unit (Mockito) | Publish success, backoff on failure, max retry boundary, empty inbox, stuck detector |
| `EventProcessorServiceTest` | Unit (Mockito) | Process new event, skip duplicate, DLT save, DLT dedup |
| `KafkaEventConsumerIntegrationTest` | Integration (TestContainers) | E2E consume, duplicate skip, DLT routing |

---

## Observability

### Grafana dashboard

Open **http://localhost:3000** (admin / admin) → Dashboards → EventPOF → **EventPOF Overview**

Panels: HTTP req/s, P99 latency, error rate, Kafka producer/consumer metrics, JVM heap, GC pause, threads, CPU.

### Logs → Loki

Both services push structured JSON logs via `loki4j` appender. Query in Grafana Explore:
```
{job=~"event-producer|event-consumer"}
```

### Metrics → Prometheus

| Service | Endpoint |
|---|---|
| event-producer | http://localhost:8080/actuator/prometheus |
| event-consumer | http://localhost:8081/prometheus |

Prometheus scrapes both every 15 s.

---

## Configuration reference

### event-producer

| Property | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | Set to `docker` when running in container |
| `spring.data.mongodb.uri` | `mongodb://...localhost:27017/...` | MongoDB connection |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka brokers |
| `inbox.relay.interval-ms` | `5000` | Relay scheduler interval (ms) |
| `LOKI_URL` | `http://localhost:3100` | Loki push endpoint |

### event-consumer

| Property | Default | Description |
|---|---|---|
| `MONGODB_URI` | `mongodb://...localhost:27017/...` | MongoDB connection |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `kafka.topics.events` | `events.domain` | Consumed topic |
| `LOKI_URL` | `http://localhost:3100` | Loki push endpoint |

---

## Project structure

```
event-pof/
├── pom.xml                        parent — Java 21, -parameters, --enable-preview
├── docker-compose.yml             full environment (infra + apps)
│
├── event-common/                  shared domain (no framework)
│
├── event-producer/                Spring Boot 4.1 — REST + Inbox Pattern
│   ├── Dockerfile
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-docker.yml   Docker profile (mongodb/kafka via container names)
│   └── src/
│
├── event-consumer/                Micronaut 4.7 — Kafka consumer
│   ├── Dockerfile
│   └── src/
│
└── infra/
    ├── mongo/init.js
    ├── loki/
    ├── promtail/
    ├── prometheus/prometheus.yml
    ├── logstash/
    └── grafana/provisioning/      datasources (uid: prometheus, loki) + EventPOF dashboard
```

---

## Known limitations (by design for PoC)

| Limitation | Production fix |
|---|---|
| No MongoDB transactions in relay | Use replica set + multi-document transactions |
| Logstash outputs to stdout only | Add Elasticsearch output + ILM policy |
| `events.domain.DLT` not monitored | Add consumer that triggers alerts (PagerDuty, Slack) |
| No schema registry | Add Confluent Schema Registry + Avro / Protobuf for `EventPayload` |
| Single Kafka partition | Increase partitions + tune consumer group for parallelism |
