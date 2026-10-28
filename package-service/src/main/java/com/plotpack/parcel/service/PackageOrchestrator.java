package com.plotpack.parcel.service;

import com.plotpack.common.web.BusinessException;
import com.plotpack.common.web.ErrorCode;
import com.plotpack.parcel.api.Dtos.CreatePackageRequest;
import com.plotpack.parcel.api.Dtos.PackageResponse;
import com.plotpack.parcel.client.UserClient;
import com.plotpack.parcel.client.UserClient.CustomerView;
import com.plotpack.parcel.domain.Bin;
import com.plotpack.parcel.domain.BinRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * POST /api/packages, step by step:
 * <pre>
 *  1. Seen this Idempotency-Key before?          -> return the first answer (no new package)
 *  2. In PARALLEL: user-service + free bins       -> validate customer, load candidate bins
 *  3. Redis lock on the customer                  -> one allocation decision per customer at a time
 *  4. Re-check idempotency inside the lock        -> a concurrent duplicate may have just finished
 *  5. reserve() a bin, then commit() it           -> retry if the bin filled up in between
 * </pre>
 */
@Service
public class PackageOrchestrator {
    private static final int MAX_ATTEMPTS = 3;

    public record Result(PackageResponse response, boolean replayed) {
    }

    private final IdempotencyService idempotency;
    private final UserClient users;
    private final BinRepository bins;
    private final RedisLockService locks;
    private final AllocationService allocation;
    private final PackageWriter writer;
    private final ExecutorService lookupExecutor;

    public PackageOrchestrator(IdempotencyService idempotency, UserClient users, BinRepository bins,
                               RedisLockService locks, AllocationService allocation, PackageWriter writer,
                               ExecutorService lookupExecutor) {
        this.idempotency = idempotency;
        this.users = users;
        this.bins = bins;
        this.locks = locks;
        this.allocation = allocation;
        this.writer = writer;
        this.lookupExecutor = lookupExecutor;
    }

    public Result create(String idempotencyKey, CreatePackageRequest req) throws InterruptedException {
        String hash = requestHash(req);
        Optional<Result> replay = replay(idempotencyKey, hash, false);
        if (replay.isPresent()) return replay.get();

        var customerF = CompletableFuture.supplyAsync(() -> users.customer(req.customerId()), lookupExecutor);
        var freeBinsF = CompletableFuture.supplyAsync(bins::findFreeBins, lookupExecutor);
        CustomerView customer = join(customerF);
        List<Bin> freeBins = join(freeBinsF);

        // Without this lock, two floors creating the FIRST package of one customer at the same instant
        // would each see "no bin yet" and grab two bins (and two e-tokens) for one person.
        try (var lock = locks.lock("lock:customer:" + customer.id(), Duration.ofSeconds(10), Duration.ofSeconds(3))) {
            if (lock.busy()) {
                throw new BusinessException(ErrorCode.ALLOCATION_BUSY, "Customer is being served on another floor, retry");
            }
            replay = replay(idempotencyKey, hash, true);
            if (replay.isPresent()) return replay.get();

            for (int attempt = 1; ; attempt++) {
                try {
                    var reservation = allocation.reserve(customer.id(), attempt == 1 ? freeBins : bins.findFreeBins());
                    PackageResponse response = writer.commit(reservation, req, customer, idempotencyKey, hash);
                    idempotency.remember(idempotencyKey, new IdempotencyService.Entry(hash, response));
                    return new Result(response, false);
                } catch (PackageWriter.StaleReservationException e) {
                    if (attempt >= MAX_ATTEMPTS) throw new BusinessException(ErrorCode.ALLOCATION_BUSY, e.getMessage());
                } catch (BusinessException e) {
                    if (e.code() != ErrorCode.ALLOCATION_BUSY || attempt >= MAX_ATTEMPTS) throw e;
                } catch (DataIntegrityViolationException duplicateKey) {
                    // Same Idempotency-Key committed in parallel (only possible if Redis was down).
                    return replay(idempotencyKey, hash, true).orElseThrow(() -> duplicateKey);
                }
            }
        }
    }

    private Optional<Result> replay(String key, String hash, boolean databaseOnly) {
        var entry = databaseOnly ? idempotency.findInDatabase(key) : idempotency.find(key);
        return entry.map(e -> {
            if (!e.requestHash().equals(hash)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Idempotency-Key reused with a different request");
            }
            return new Result(e.response(), true);
        });
    }

    private static <T> T join(CompletableFuture<T> f) {
        try {
            return f.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    static String requestHash(CreatePackageRequest r) {
        String canonical = r.customerId() + "|" + r.floor() + "|" + r.description() + "|"
                + r.amount().stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
