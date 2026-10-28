package com.plotpack.parcel.service;

import com.plotpack.common.web.BusinessException;
import com.plotpack.common.web.ErrorCode;
import com.plotpack.parcel.domain.Bin;
import com.plotpack.parcel.domain.BinAllocation;
import com.plotpack.parcel.domain.BinAllocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Picks the bin for a new package:
 * <ol>
 *   <li>Customer already has a bin with space? Reuse it (keep one customer's items together).</li>
 *   <li>Customer already holds the max number of bins? Reject.</li>
 *   <li>Otherwise walk the free bins, nearest first: Redis lock -> re-check -> insert guarded by UNIQUE.</li>
 * </ol>
 */
@Service
public class AllocationService {
    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);
    private static final Duration BIN_LOCK_TTL = Duration.ofSeconds(5);

    public record Reservation(Long allocationId, String binCode, boolean newBin) {
    }

    private final BinAllocationRepository allocations;
    private final BinAllocator binAllocator;
    private final RedisLockService locks;
    private final int maxBinsPerCustomer;

    public AllocationService(BinAllocationRepository allocations, BinAllocator binAllocator, RedisLockService locks,
                             @Value("${plotpack.max-bins-per-customer:3}") int maxBinsPerCustomer) {
        this.allocations = allocations;
        this.binAllocator = binAllocator;
        this.locks = locks;
        this.maxBinsPerCustomer = maxBinsPerCustomer;
    }

    public Reservation reserve(Long customerId, List<Bin> freeBins) {
        List<BinAllocation> mine = allocations.findByCustomerIdAndStatusOrderByAllocatedAt(customerId, BinAllocation.Status.ACTIVE);
        for (BinAllocation a : mine) {
            if (a.hasSpace()) {
                return new Reservation(a.getId(), a.getBinCode(), false);
            }
        }
        if (mine.size() >= maxBinsPerCustomer) {
            throw new BusinessException(ErrorCode.MAX_BINS_PER_CUSTOMER,
                    "Customer already fills " + mine.size() + " bins (limit " + maxBinsPerCustomer + ")");
        }
        if (freeBins.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_FREE_BIN, "All bins are occupied. Retry after the next pickup.");
        }

        for (Bin bin : freeBins) {
            try (var lock = locks.tryLock("lock:bin:" + bin.getId(), BIN_LOCK_TTL)) {
                if (lock.busy()) {
                    continue; // another request is claiming this bin right now -> try the next one
                }
                BinAllocation claimed = binAllocator.tryAllocate(bin, customerId);
                log.info("Customer {} got new bin {}", customerId, bin.getCode());
                return new Reservation(claimed.getId(), claimed.getBinCode(), true);
            } catch (BinAllocator.BinTakenException | DataIntegrityViolationException lostRace) {
                log.info("Lost race for bin {}, trying next", bin.getCode());
            }
        }
        throw new BusinessException(ErrorCode.ALLOCATION_BUSY, "Every free bin was being claimed concurrently, retry");
    }
}
