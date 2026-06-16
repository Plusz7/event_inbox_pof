package com.eventpof.producer.domain.inbox;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InboxEventRepository extends MongoRepository<InboxEvent, String> {
    Optional<InboxEvent> findByEventKey(String eventKey);
    boolean existsByEventKey(String eventKey);
}
