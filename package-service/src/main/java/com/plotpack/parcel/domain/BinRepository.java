package com.plotpack.parcel.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BinRepository extends JpaRepository<Bin, Long> {

    List<Bin> findAllByOrderByDistanceRankAsc();

    /** Bins with no ACTIVE allocation, nearest to the pickup counter first. */
    @Query("""
            SELECT b FROM Bin b
            WHERE NOT EXISTS (SELECT 1 FROM BinAllocation a WHERE a.activeSlot = b.id)
            ORDER BY b.distanceRank""")
    List<Bin> findFreeBins();
}
