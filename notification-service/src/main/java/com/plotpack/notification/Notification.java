package com.plotpack.notification;

import jakarta.persistence.*;

import java.time.Instant;

/** An SMS sent to the customer. event_id is UNIQUE, so a redelivered Kafka event never sends twice. */
@Entity
@Table(name = "notification")
public class Notification {
    public enum Kind { E_TOKEN, DELIVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, length = 16)
    private String token;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 400)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(String eventId, Kind kind, String token, String customerName, String phone, String message) {
        this.eventId = eventId;
        this.kind = kind;
        this.token = token;
        this.customerName = customerName;
        this.phone = phone;
        this.message = message;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public Kind getKind() { return kind; }
    public String getToken() { return token; }
    public String getCustomerName() { return customerName; }
    public String getPhone() { return phone; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
