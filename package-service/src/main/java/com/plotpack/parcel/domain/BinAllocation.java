package com.plotpack.parcel.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * "Customer X is using bin Y and has N packages in it."
 *
 * <p>{@code activeSlot} = bin id while ACTIVE, NULL once RELEASED. A UNIQUE index on it means the
 * database refuses a second ACTIVE allocation of the same bin, while keeping any number of old
 * RELEASED rows (unique indexes ignore NULLs). This constraint is the final guard against double
 * allocation; the Redis lock in front of it only reduces collisions.
 */
@Entity
@Table(name = "bin_allocation")
public class BinAllocation {
    public enum Status { ACTIVE, RELEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bin_id", nullable = false)
    private Long binId;

    @Column(name = "bin_code", nullable = false, length = 16)
    private String binCode;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "package_count", nullable = false)
    private int packageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "active_slot", unique = true)
    private Long activeSlot;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt = Instant.now();

    @Column(name = "released_at")
    private Instant releasedAt;

    protected BinAllocation() {
    }

    public BinAllocation(Bin bin, Long customerId) {
        this.binId = bin.getId();
        this.binCode = bin.getCode();
        this.capacity = bin.getCapacity();
        this.customerId = customerId;
        this.activeSlot = bin.getId();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean hasSpace() {
        return isActive() && packageCount < capacity;
    }

    public void addPackage() {
        if (!hasSpace()) {
            throw new IllegalStateException("Bin " + binCode + " is full or released");
        }
        packageCount++;
    }

    /** @return true when the last package left and the bin went back to the free pool. */
    public boolean removePackage() {
        if (packageCount > 0) {
            packageCount--;
        }
        if (packageCount == 0 && isActive()) {
            status = Status.RELEASED;
            activeSlot = null;
            releasedAt = Instant.now();
            return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public Long getBinId() { return binId; }
    public String getBinCode() { return binCode; }
    public Long getCustomerId() { return customerId; }
    public int getCapacity() { return capacity; }
    public int getPackageCount() { return packageCount; }
    public Status getStatus() { return status; }
    public Instant getAllocatedAt() { return allocatedAt; }
}
