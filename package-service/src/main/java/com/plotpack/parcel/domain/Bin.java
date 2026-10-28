package com.plotpack.parcel.domain;

import jakarta.persistence.*;

/** A physical slot in the ground-floor grid, addressed RACK-ROW-COL (e.g. B-2-04). Seeded by Flyway. */
@Entity
@Table(name = "bin")
public class Bin {
    @Id
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 4)
    private String rack;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "col_no", nullable = false)
    private int colNo;

    @Column(nullable = false)
    private int capacity;

    /** Walking distance from the pickup counter; nearest bins are allocated first. */
    @Column(name = "distance_rank", nullable = false)
    private int distanceRank;

    protected Bin() {
    }

    public Bin(Long id, String code, int capacity) {
        this.id = id;
        this.code = code;
        this.capacity = capacity;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getRack() { return rack; }
    public int getRowNo() { return rowNo; }
    public int getColNo() { return colNo; }
    public int getCapacity() { return capacity; }
    public int getDistanceRank() { return distanceRank; }
}
