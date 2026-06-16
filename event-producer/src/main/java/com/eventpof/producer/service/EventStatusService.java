package com.eventpof.producer.service;

import com.eventpof.producer.domain.inbox.InboxEvent;
import com.eventpof.producer.domain.inbox.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final InboxEventRepository inboxEventRepository;

    public Optional<InboxEvent> findById(String inboxId) {
        return inboxEventRepository.findById(inboxId);
    }
}
