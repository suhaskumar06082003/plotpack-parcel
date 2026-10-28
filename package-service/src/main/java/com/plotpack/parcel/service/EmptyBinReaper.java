package com.plotpack.parcel.service;

import com.plotpack.parcel.domain.BinAllocation;
import com.plotpack.parcel.domain.BinAllocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Safety net: a bin is claimed in its own transaction before the package is saved. If the request
 * dies in between, the bin would stay ACTIVE with 0 packages forever. This frees such bins.
 */
@Component
public class EmptyBinReaper {
    private static final Logger log = LoggerFactory.getLogger(EmptyBinReaper.class);

    private final BinAllocationRepository allocations;

    public EmptyBinReaper(BinAllocationRepository allocations) {
        this.allocations = allocations;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @Transactional
    public void releaseEmptyBins() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(30));
        for (BinAllocation a : allocations.findByStatus(BinAllocation.Status.ACTIVE)) {
            if (a.getPackageCount() == 0 && a.getAllocatedAt().isBefore(cutoff)) {
                allocations.findByIdForUpdate(a.getId())
                        .filter(locked -> locked.getPackageCount() == 0 && locked.isActive())
                        .ifPresent(locked -> {
                            locked.removePackage();
                            log.info("Released orphan empty bin {}", locked.getBinCode());
                        });
            }
        }
    }
}
