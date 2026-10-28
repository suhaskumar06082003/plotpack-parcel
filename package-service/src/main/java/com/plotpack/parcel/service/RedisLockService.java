package com.plotpack.parcel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Short-lived distributed lock: SET key token NX PX ttl.
 * The TTL means a crashed instance can't hold a lock forever; the token means we only delete our own lock.
 * If Redis is down the lock reports UNAVAILABLE and we carry on - the DB constraints still keep data correct.
 */
@Component
public class RedisLockService {
    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    public enum State { ACQUIRED, BUSY, UNAVAILABLE }

    public final class Lock implements AutoCloseable {
        private final String key;
        private final String token;
        private final State state;

        private Lock(String key, String token, State state) {
            this.key = key;
            this.token = token;
            this.state = state;
        }

        public boolean busy() {
            return state == State.BUSY;
        }

        @Override
        public void close() {
            if (state != State.ACQUIRED) return;
            try {
                redis.execute(UNLOCK, List.of(key), token);
            } catch (DataAccessException e) {
                log.warn("Could not release {} (it will expire by TTL)", key);
            }
        }
    }

    private final StringRedisTemplate redis;

    public RedisLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** One attempt. Used per bin: if busy, the caller just tries the next bin. */
    public Lock tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            boolean ok = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl));
            return new Lock(key, token, ok ? State.ACQUIRED : State.BUSY);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, continuing without lock {}", key);
            return new Lock(key, token, State.UNAVAILABLE);
        }
    }

    /** Waits up to maxWait. Used per customer: requests for the same customer queue up instead of skipping. */
    public Lock lock(String key, Duration ttl, Duration maxWait) throws InterruptedException {
        long deadline = System.nanoTime() + maxWait.toNanos();
        Lock lock = tryLock(key, ttl);
        while (lock.busy() && System.nanoTime() < deadline) {
            Thread.sleep(15);
            lock = tryLock(key, ttl);
        }
        return lock;
    }
}
