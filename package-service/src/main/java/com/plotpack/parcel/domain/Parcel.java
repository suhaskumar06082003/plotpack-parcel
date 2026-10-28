package com.plotpack.parcel.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** One purchased item, packed upstairs and placed in a ground-floor bin until pickup. */
@Entity
@Table(name = "parcel")
public class Parcel {
    public enum Status { ALLOCATED, DELIVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The e-token sent to the customer; all of one visit's packages share it. */
    @Column(nullable = false, length = 16)
    private String token;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;

    @Column(name = "bin_id", nullable = false)
    private Long binId;

    @Column(name = "bin_code", nullable = false, length = 16)
    private String binCode;

    @Column(nullable = false)
    private int floor;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ALLOCATED;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected Parcel() {
    }

    public Parcel(String token, Long customerId, String customerName, String customerPhone, BinAllocation allocation,
                  int floor, String description, BigDecimal amount, String idempotencyKey, String requestHash) {
        this.token = token;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.allocationId = allocation.getId();
        this.binId = allocation.getBinId();
        this.binCode = allocation.getBinCode();
        this.floor = floor;
        this.description = description;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void markDelivered() {
        status = Status.DELIVERED;
        deliveredAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public Long getAllocationId() { return allocationId; }
    public Long getBinId() { return binId; }
    public String getBinCode() { return binCode; }
    public int getFloor() { return floor; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
}
