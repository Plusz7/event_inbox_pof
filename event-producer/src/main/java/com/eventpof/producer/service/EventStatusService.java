package com.eventpof.producer.service;

import com.eventpof.producer.domain.outbox.OutboxEvent;
import com.eventpof.producer.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final OutboxEventRepository outboxEventRepository;

    public Optional<OutboxEvent> findById(String outboxId) {
        return outboxEventRepository.findById(outboxId);
    }
}
