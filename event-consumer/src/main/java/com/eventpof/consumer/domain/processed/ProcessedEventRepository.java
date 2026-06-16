package com.eventpof.consumer.domain.processed;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import java.util.Optional;

// Micronaut Data generates the MongoDB implementation at compile time
@MongoRepository(databaseName = "${mongodb.database:eventpof}")
public interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, ObjectId> {

    boolean existsByEventKey(String eventKey);

    Optional<ProcessedEvent> findByEventKey(String eventKey);

    // count() and deleteAll() are inherited from CrudRepository
}
