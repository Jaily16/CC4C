package com.cc4c.shared;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class InboxRepository {
    private final JdbcTemplate jdbc;

    InboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public InboxClaim claim(String consumerName, String eventId, int generation, String workerId, Instant leaseUntil) {
        int inserted = jdbc.update(
                """
                INSERT IGNORE INTO async_inbox(
                    consumer_name, event_id, generation, status, attempts, lease_owner, lease_until)
                VALUES(?, ?, ?, 'PROCESSING', 1, ?, ?)
                """,
                consumerName,
                eventId,
                generation,
                workerId,
                Timestamp.from(leaseUntil));
        List<InboxRow> rows = jdbc.query(
                """
                SELECT status, lease_owner, lease_until FROM async_inbox
                WHERE consumer_name = ? AND event_id = ? AND generation = ?
                FOR UPDATE
                """,
                (result, rowNumber) -> new InboxRow(
                        result.getString("status"),
                        result.getString("lease_owner"),
                        result.getTimestamp("lease_until") == null
                                ? null
                                : result.getTimestamp("lease_until").toInstant()),
                consumerName,
                eventId,
                generation);
        InboxRow row = rows.get(0);
        if (inserted == 1) {
            return InboxClaim.ACQUIRED;
        }
        if ("DONE".equals(row.status())) {
            return InboxClaim.ALREADY_DONE;
        }
        if ("PROCESSING".equals(row.status())
                && row.leaseUntil() != null
                && row.leaseUntil().isAfter(Instant.now())
                && !workerId.equals(row.leaseOwner())) {
            return InboxClaim.ALREADY_PROCESSING;
        }
        jdbc.update(
                """
                UPDATE async_inbox
                SET status = 'PROCESSING', attempts = attempts + 1, lease_owner = ?, lease_until = ?,
                    error_code = NULL
                WHERE consumer_name = ? AND event_id = ? AND generation = ?
                """,
                workerId,
                Timestamp.from(leaseUntil),
                consumerName,
                eventId,
                generation);
        return InboxClaim.ACQUIRED;
    }

    void markRetryWaiting(String consumerName, String eventId, int generation, String errorCode) {
        jdbc.update(
                """
                UPDATE async_inbox
                SET status = 'RETRY_WAIT', error_code = ?, lease_owner = NULL, lease_until = NULL
                WHERE consumer_name = ? AND event_id = ? AND generation = ?
                """,
                errorCode,
                consumerName,
                eventId,
                generation);
    }

    void markDone(String consumerName, String eventId, int generation) {
        jdbc.update(
                """
                UPDATE async_inbox
                SET status = 'DONE', processed_at = CURRENT_TIMESTAMP(3), error_code = NULL,
                    lease_owner = NULL, lease_until = NULL
                WHERE consumer_name = ? AND event_id = ? AND generation = ?
                """,
                consumerName,
                eventId,
                generation);
    }

    void markDead(String consumerName, String eventId, int generation, String errorCode) {
        jdbc.update(
                """
                UPDATE async_inbox
                SET status = 'DEAD', processed_at = CURRENT_TIMESTAMP(3), error_code = ?,
                    lease_owner = NULL, lease_until = NULL
                WHERE consumer_name = ? AND event_id = ? AND generation = ?
                """,
                errorCode,
                consumerName,
                eventId,
                generation);
    }

    int cleanupDone(Instant before, int limit) {
        return jdbc.update(
                """
                DELETE FROM async_inbox
                WHERE status = 'DONE' AND processed_at < ?
                ORDER BY processed_at LIMIT ?
                """,
                Timestamp.from(before),
                limit);
    }

    Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS count_value FROM async_inbox GROUP BY status", result -> {
            counts.put(result.getString("status"), result.getLong("count_value"));
        });
        return Map.copyOf(counts);
    }

    private record InboxRow(String status, String leaseOwner, Instant leaseUntil) {}
}
