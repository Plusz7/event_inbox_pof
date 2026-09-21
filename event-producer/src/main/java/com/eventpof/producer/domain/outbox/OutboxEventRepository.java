package com.eventpof.producer.domain.outbox;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {
    Optional<OutboxEvent> findByEventKey(String eventKey);
    boolean existsByEventKey(String eventKey);
}
