package com.eventpof.consumer.domain.processed;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import java.util.Optional;

@MongoRepository(databaseName = "${mongodb.database:eventpof}")
public interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, ObjectId> {

    boolean existsByEventKey(String eventKey);

    Optional<ProcessedEvent> findByEventKey(String eventKey);
}
