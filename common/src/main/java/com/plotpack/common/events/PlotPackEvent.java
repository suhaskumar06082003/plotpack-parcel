package com.plotpack.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope for every event on Kafka. {@code eventId} lets consumers drop duplicates
 * (Kafka delivers at-least-once).
 */
public record PlotPackEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String aggregateId,
        Map<String, Object> payload) {

    public static PlotPackEvent of(String eventType, String aggregateId, Map<String, Object> payload) {
        return new PlotPackEvent(UUID.randomUUID().toString(), eventType, Instant.now(), aggregateId, payload);
    }

    public String str(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : v.toString();
    }
}
