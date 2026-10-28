package com.plotpack.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plotpack.common.events.PlotPackEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an event into the outbox table. MANDATORY propagation guarantees the event row commits
 * atomically with the business state change that produced it (transactional outbox pattern).
 */
@Component
public class OutboxWriter {
    private final OutboxRepository repository;
    private final ObjectMapper mapper;

    public OutboxWriter(OutboxRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, PlotPackEvent event) {
        try {
            repository.save(new OutboxEvent(event.eventId(), topic, key, event.eventType(), mapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.eventType(), e);
        }
    }
}
