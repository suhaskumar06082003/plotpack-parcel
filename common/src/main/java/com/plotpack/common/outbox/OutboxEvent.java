package com.plotpack.common.outbox;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    public enum Status { PENDING, SENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "msg_key", nullable = false)
    private String msgKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventId, String topic, String msgKey, String eventType, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.msgKey = msgKey;
        this.eventType = eventType;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getMsgKey() { return msgKey; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailedAttempt() {
        this.attempts++;
    }
}
