package com.plotpack.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plotpack.parcel.api.Dtos.PackageResponse;
import com.plotpack.parcel.domain.ParcelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Same Idempotency-Key => same answer, never a second package.
 * Redis (24h) is the fast path; MySQL UNIQUE(idempotency_key) is the source of truth,
 * so a Redis flush or outage never causes duplicates. "Redis for speed, MySQL for correctness."
 */
@Service
public class IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration TTL = Duration.ofHours(24);

    public record Entry(String requestHash, PackageResponse response) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ParcelRepository parcels;
    private final PackageWriter writer;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper mapper, ParcelRepository parcels, PackageWriter writer) {
        this.redis = redis;
        this.mapper = mapper;
        this.parcels = parcels;
        this.writer = writer;
    }

    public Optional<Entry> find(String key) {
        try {
            String cached = redis.opsForValue().get("idem:" + key);
            if (cached != null) {
                return Optional.of(mapper.readValue(cached, Entry.class));
            }
        } catch (Exception e) {
            log.debug("Redis miss/unavailable for idempotency, checking MySQL");
        }
        return findInDatabase(key);
    }

    public Optional<Entry> findInDatabase(String key) {
        return parcels.findByIdempotencyKey(key).map(p -> {
            Entry entry = new Entry(p.getRequestHash(), writer.describe(p, false));
            remember(key, entry);
            return entry;
        });
    }

    public void remember(String key, Entry entry) {
        try {
            redis.opsForValue().set("idem:" + key, mapper.writeValueAsString(entry), TTL);
        } catch (Exception e) {
            log.debug("Could not cache idempotent response: {}", e.getMessage());
        }
    }
}
