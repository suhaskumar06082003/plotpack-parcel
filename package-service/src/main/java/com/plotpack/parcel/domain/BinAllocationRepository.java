package com.plotpack.parcel.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BinAllocationRepository extends JpaRepository<BinAllocation, Long> {

    /** PESSIMISTIC_WRITE = SELECT ... FOR UPDATE: one writer at a time per bin row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BinAllocation a WHERE a.id = :id")
    Optional<BinAllocation> findByIdForUpdate(Long id);

    List<BinAllocation> findByCustomerIdAndStatusOrderByAllocatedAt(Long customerId, BinAllocation.Status status);

    List<BinAllocation> findByStatus(BinAllocation.Status status);

    boolean existsByActiveSlot(Long activeSlot);
}
