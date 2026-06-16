package com.eventpof.producer.domain.inbox;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InboxEventRepository extends MongoRepository<InboxEvent, String> {
    List<InboxEvent> findTop10ByStatusOrderByCreatedAtAsc(InboxEventStatus status);
    Optional<InboxEvent> findByEventKey(String eventKey);
    boolean existsByEventKey(String eventKey);
}
