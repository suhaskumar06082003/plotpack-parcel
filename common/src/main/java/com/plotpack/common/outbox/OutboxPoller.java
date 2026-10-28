package com.plotpack.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Relays committed outbox rows to Kafka. If the broker is down the rows stay PENDING and are
 * retried on the next tick, so no event is lost between the DB commit and the publish.
 */
@Component
public class OutboxPoller {
    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH = 100;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPoller(OutboxRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${plotpack.outbox.poll-interval-ms:250}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockPendingBatch(BATCH);
        if (batch.isEmpty()) return;

        // Send the whole batch at once (the producer pipelines it), then wait for the acks.
        List<CompletableFuture<SendResult<String, String>>> acks = new ArrayList<>(batch.size());
        for (OutboxEvent row : batch) {
            acks.add(kafka.send(row.getTopic(), row.getMsgKey(), row.getPayload()));
        }
        for (int i = 0; i < batch.size(); i++) {
            OutboxEvent row = batch.get(i);
            try {
                acks.get(i).get(5, TimeUnit.SECONDS);
                row.markSent();
            } catch (Exception e) {
                row.markFailedAttempt();
                log.warn("Outbox publish failed for {} ({}), attempt {} - will retry: {}",
                        row.getEventId(), row.getEventType(), row.getAttempts(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void purgeOldSentRows() {
        int purged = repository.purgeSentBefore(Instant.now().minus(Duration.ofDays(1)));
        if (purged > 0) {
            log.info("Purged {} published outbox rows", purged);
        }
    }
}
