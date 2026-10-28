package com.plotpack.parcel.service;

import com.plotpack.parcel.domain.Bin;
import com.plotpack.parcel.domain.BinAllocation;
import com.plotpack.parcel.domain.BinAllocationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One attempt to claim one free bin, in its OWN transaction (REQUIRES_NEW).
 * If the bin was taken a moment ago only this tiny transaction rolls back,
 * and the caller moves on to the next bin with a clean slate.
 */
@Component
public class BinAllocator {

    public static class BinTakenException extends RuntimeException {
        public BinTakenException(String binCode) {
            super("Bin " + binCode + " was just taken");
        }
    }

    private final BinAllocationRepository allocations;

    public BinAllocator(BinAllocationRepository allocations) {
        this.allocations = allocations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BinAllocation tryAllocate(Bin bin, Long customerId) {
        // Re-check inside the lock: the free-bin list was read before we held it.
        if (allocations.existsByActiveSlot(bin.getId())) {
            throw new BinTakenException(bin.getCode());
        }
        // If two requests still race (e.g. Redis down), UNIQUE(active_slot) rejects the second insert.
        return allocations.saveAndFlush(new BinAllocation(bin, customerId));
    }
}
