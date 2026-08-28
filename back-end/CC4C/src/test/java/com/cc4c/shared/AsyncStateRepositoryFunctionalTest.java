package com.cc4c.shared;

import com.cc4c.functional.FunctionalTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncStateRepositoryFunctionalTest extends FunctionalTestSupport {

    @Autowired
    private InboxRepository inbox;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TransactionalOutbox transactionalOutbox;

    @Test
    void inboxPreventsConcurrentHandlingAllowsExpiredLeaseTakeoverAndKeepsGenerationsIndependent() {
        String eventId = UUID.randomUUID().toString();
        Instant future = Instant.now().plusSeconds(60);

        assertEquals(InboxClaim.ACQUIRED,
                inbox.claim("consumer", eventId, 0, "worker-a", future));
        assertEquals(InboxClaim.ALREADY_PROCESSING,
                inbox.claim("consumer", eventId, 0, "worker-b", future));

        jdbcTemplate.update("""
                UPDATE async_inbox SET lease_until = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND)
                WHERE consumer_name = 'consumer' AND event_id = ? AND generation = 0
                """, eventId);
        assertEquals(InboxClaim.ACQUIRED,
                inbox.claim("consumer", eventId, 0, "worker-b", future));
        inbox.markDone("consumer", eventId, 0);
        assertEquals(InboxClaim.ALREADY_DONE,
                inbox.claim("consumer", eventId, 0, "worker-c", future));

        assertEquals(InboxClaim.ACQUIRED,
                inbox.claim("consumer", eventId, 1, "worker-c", future));
    }

    @Test
    void retentionCleanupDeletesOnlyCompletedOldRows() {
        Instant occurredAt = Instant.now();
        String delivered = transactionalOutbox.append(
                AsyncEventTypes.BLOG_SUBMITTED, "blog", "cleanup-delivered",
                Map.of("safe", true), occurredAt, null);
        String pending = transactionalOutbox.append(
                AsyncEventTypes.BLOG_SUBMITTED, "blog", "cleanup-pending",
                Map.of("safe", true), occurredAt, null);
        jdbcTemplate.update("""
                UPDATE async_outbox
                SET status = 'DELIVERED', updated_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 32 DAY)
                WHERE event_id = ?
                """, delivered);
        jdbcTemplate.update("""
                UPDATE async_outbox
                SET updated_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 32 DAY)
                WHERE event_id = ?
                """, pending);

        String inboxDone = UUID.randomUUID().toString();
        String inboxDead = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO async_inbox(
                    consumer_name, event_id, generation, status, attempts, processed_at)
                VALUES('cleanup', ?, 0, 'DONE', 1, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 32 DAY)),
                      ('cleanup', ?, 0, 'DEAD', 1, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 32 DAY))
                """, inboxDone, inboxDead);

        assertTrue(outboxRepository.cleanupCompleted(
                Instant.now().minus(31, ChronoUnit.DAYS), 500) >= 1);
        assertEquals(1, inbox.cleanupDone(
                Instant.now().minus(31, ChronoUnit.DAYS), 500));

        assertFalse(outboxRepository.findByEventId(delivered).isPresent());
        assertTrue(outboxRepository.findByEventId(pending).isPresent());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM async_inbox WHERE event_id = ?", Integer.class, inboxDone));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM async_inbox WHERE event_id = ?", Integer.class, inboxDead));
    }

    @Test
    void expiredPublishingLeaseIsReclaimedByAnotherDispatcher() {
        Instant occurredAt = Instant.now().minusSeconds(1);
        String eventId = transactionalOutbox.append(
                AsyncEventTypes.BLOG_SUBMITTED, "blog", "lease-recovery",
                Map.of("safe", true), occurredAt, null);
        jdbcTemplate.update(
                "UPDATE async_outbox SET status = 'PUBLISHED' WHERE event_id <> ? AND status IN ('PENDING','PUBLISHING')",
                eventId);
        jdbcTemplate.update("""
                UPDATE async_outbox
                SET status = 'PUBLISHING', lease_owner = 'crashed-worker',
                    lease_until = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND)
                WHERE event_id = ?
                """, eventId);

        List<OutboxMessage> reclaimed = outboxRepository.claimBatch(
                "replacement-worker", 50, Instant.now().plusSeconds(30));

        assertEquals(List.of(eventId), reclaimed.stream().map(OutboxMessage::eventId).toList());
    }
}
