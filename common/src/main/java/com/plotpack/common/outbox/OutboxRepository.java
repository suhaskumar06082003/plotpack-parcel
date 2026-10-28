package com.plotpack.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** SKIP LOCKED lets several service instances poll the same table without publishing a row twice. */
    @Query(value = "SELECT * FROM outbox_event WHERE status = 'PENDING' ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(int limit);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = com.plotpack.common.outbox.OutboxEvent.Status.SENT AND o.createdAt < :before")
    int purgeSentBefore(Instant before);
}
