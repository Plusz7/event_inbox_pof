#!/usr/bin/env bash
set -euo pipefail

echo "==> Starting infrastructure..."
docker compose up -d zookeeper kafka mongodb loki promtail grafana prometheus logstash

echo "==> Waiting for Kafka to be ready..."
until docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list &>/dev/null; do
  sleep 2
done

echo "==> Creating Kafka topics..."
docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic events.domain --partitions 3 --replication-factor 1
docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic events.domain.DLT --partitions 3 --replication-factor 1

echo ""
echo "Infrastructure ready. Start the apps:"
echo "  event-producer: cd event-producer && mvn spring-boot:run"
echo "  event-consumer: cd event-consumer && mvn spring-boot:run"
echo ""
echo "URLs:"
echo "  Kafka UI:      http://localhost:9080"
echo "  Mongo Express: http://localhost:8082"
echo "  Grafana:       http://localhost:3000  (admin/admin)"
echo "  Prometheus:    http://localhost:9090"
echo "  Loki:          http://localhost:3100"
echo "  Producer API:  http://localhost:8080/api/v1/events"
echo "  Consumer Act:  http://localhost:8081/actuator/health"
