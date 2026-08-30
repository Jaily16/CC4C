package com.cc4c.shared;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class OutboxRepository {
    private static final String COLUMNS =
            """
            id, event_id, correlation_id, schema_version, event_type, aggregate_type,
            aggregate_id, routing_key,
            generation, status, publish_attempts, consume_attempts, payload_key_id,
            payload_nonce, payload_ciphertext, occurred_at, expires_at, created_at, updated_at,
            failed_at, error_code
            """;
    private static final RowMapper<OutboxMessage> ROW_MAPPER = OutboxRepository::map;

    private final JdbcTemplate jdbc;

    OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(
            String eventId,
            String correlationId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String routingKey,
            Instant occurredAt,
            Instant expiresAt,
            EncryptedMessagePayload payload,
            OutboxStatus initialStatus,
            String errorCode) {
        jdbc.update(
                """
                INSERT INTO async_outbox(
                    event_id, correlation_id, schema_version, event_type, aggregate_type,
                    aggregate_id, routing_key,
                    generation, status, next_attempt_at, payload_key_id, payload_nonce,
                    payload_ciphertext, occurred_at, expires_at, failed_at, error_code)
                VALUES(?, ?, 1, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId,
                correlationId,
                eventType,
                aggregateType,
                aggregateId,
                routingKey,
                initialStatus.name(),
                timestamp(occurredAt),
                payload.keyId(),
                payload.nonce(),
                payload.ciphertext(),
                timestamp(occurredAt),
                timestamp(expiresAt),
                initialStatus == OutboxStatus.DEAD ? timestamp(occurredAt) : null,
                errorCode);
    }

    @Transactional
    public List<OutboxMessage> claimBatch(String workerId, int limit, Instant leaseUntil) {
        List<Long> ids = jdbc.queryForList(
                """
                SELECT id FROM async_outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP(3))
                   OR (status = 'PUBLISHING' AND lease_until < CURRENT_TIMESTAMP(3))
                ORDER BY id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """,
                Long.class,
                limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        for (Long id : ids) {
            jdbc.update(
                    """
                    UPDATE async_outbox
                    SET status = 'PUBLISHING', lease_owner = ?, lease_until = ?, error_code = NULL
                    WHERE id = ?
                    """,
                    workerId,
                    timestamp(leaseUntil),
                    id);
        }
        String placeholders = String.join(",", ids.stream().map(ignored -> "?").toList());
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM async_outbox WHERE id IN (" + placeholders + ") ORDER BY id",
                ROW_MAPPER,
                ids.toArray());
    }

    void markPublished(String eventId, int generation) {
        jdbc.update(
                """
                UPDATE async_outbox
                SET status = 'PUBLISHED', publish_attempts = publish_attempts + 1,
                    published_at = CURRENT_TIMESTAMP(3), lease_owner = NULL, lease_until = NULL,
                    error_code = NULL
                WHERE event_id = ? AND generation = ? AND status = 'PUBLISHING'
                """,
                eventId,
                generation);
    }

    void markPublishFailure(String eventId, int generation, String errorCode, Instant nextAttempt, boolean terminal) {
        jdbc.update(
                """
                UPDATE async_outbox
                SET status = ?, publish_attempts = publish_attempts + 1, next_attempt_at = ?,
                    failed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP(3) ELSE failed_at END,
                    error_code = ?, lease_owner = NULL, lease_until = NULL
                WHERE event_id = ? AND generation = ? AND status = 'PUBLISHING'
                """,
                terminal ? OutboxStatus.PUBLISH_FAILED.name() : OutboxStatus.PENDING.name(),
                timestamp(nextAttempt),
                terminal,
                errorCode,
                eventId,
                generation);
    }

    void markDelivered(String eventId, int generation) {
        jdbc.update(
                """
                UPDATE async_outbox
                SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP(3), error_code = NULL
                WHERE event_id = ? AND generation = ? AND status IN ('PUBLISHED', 'PUBLISHING')
                """,
                eventId,
                generation);
    }

    void incrementConsumeAttempt(String eventId, int generation) {
        jdbc.update(
                """
                UPDATE async_outbox SET consume_attempts = consume_attempts + 1
                WHERE event_id = ? AND generation = ?
                """,
                eventId,
                generation);
    }

    void markDead(String eventId, int generation, String errorCode) {
        jdbc.update(
                """
                UPDATE async_outbox
                SET status = 'DEAD', failed_at = CURRENT_TIMESTAMP(3), error_code = ?
                WHERE event_id = ? AND generation = ?
                """,
                errorCode,
                eventId,
                generation);
    }

    void markExpired(String eventId, int generation) {
        jdbc.update(
                """
                UPDATE async_outbox
                SET status = 'EXPIRED', failed_at = CURRENT_TIMESTAMP(3), error_code = 'MESSAGE_EXPIRED'
                WHERE event_id = ? AND generation = ?
                """,
                eventId,
                generation);
    }

    Optional<OutboxMessage> findByEventId(String eventId) {
        List<OutboxMessage> rows =
                jdbc.query("SELECT " + COLUMNS + " FROM async_outbox WHERE event_id = ?", ROW_MAPPER, eventId);
        return rows.stream().findFirst();
    }

    PageResult<AsyncMessageSummary> findPage(OutboxStatus status, String eventType, PageQuery query) {
        StringBuilder where = new StringBuilder();
        List<Object> arguments = new ArrayList<>();
        if (status == null) {
            where.append(" WHERE status IN ('PENDING','PUBLISHING','PUBLISHED','PUBLISH_FAILED','DEAD')");
        } else {
            where.append(" WHERE status = ?");
            arguments.add(status.name());
        }
        if (eventType != null && !eventType.isBlank()) {
            where.append(" AND event_type = ?");
            arguments.add(eventType);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM async_outbox" + where, Long.class, arguments.toArray());
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(query.size());
        pageArguments.add(query.offset());
        List<AsyncMessageSummary> items = jdbc
                .query(
                        "SELECT " + COLUMNS + " FROM async_outbox" + where
                                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                        ROW_MAPPER,
                        pageArguments.toArray())
                .stream()
                .map(AsyncMessageSummary::from)
                .toList();
        return new PageResult<>(items, query.page(), query.size(), total);
    }

    int resetForManualRetry(OutboxMessage message, EncryptedMessagePayload encrypted, int nextGeneration) {
        return jdbc.update(
                """
                UPDATE async_outbox
                SET generation = ?, status = 'PENDING', publish_attempts = 0, consume_attempts = 0,
                    next_attempt_at = CURRENT_TIMESTAMP(3), lease_owner = NULL, lease_until = NULL,
                    payload_key_id = ?, payload_nonce = ?, payload_ciphertext = ?,
                    published_at = NULL, delivered_at = NULL, failed_at = NULL, error_code = NULL,
                    ignored_by = NULL, ignored_at = NULL
                WHERE event_id = ? AND generation = ? AND status IN ('PUBLISH_FAILED', 'DEAD')
                """,
                nextGeneration,
                encrypted.keyId(),
                encrypted.nonce(),
                encrypted.ciphertext(),
                message.eventId(),
                message.generation());
    }

    int ignore(String eventId, int generation, String actorId) {
        return jdbc.update(
                """
                UPDATE async_outbox
                SET status = 'IGNORED', ignored_by = ?, ignored_at = CURRENT_TIMESTAMP(3),
                    lease_owner = NULL, lease_until = NULL
                WHERE event_id = ? AND generation = ? AND status IN ('PUBLISH_FAILED', 'DEAD')
                """,
                actorId,
                eventId,
                generation);
    }

    int cleanupCompleted(Instant before, int limit) {
        return jdbc.update(
                """
                DELETE FROM async_outbox
                WHERE status IN ('DELIVERED', 'EXPIRED', 'IGNORED') AND updated_at < ?
                ORDER BY id LIMIT ?
                """,
                timestamp(before),
                limit);
    }

    Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS count_value FROM async_outbox GROUP BY status", result -> {
            counts.put(result.getString("status"), result.getLong("count_value"));
        });
        return Map.copyOf(counts);
    }

    double oldestPendingSeconds() {
        Double seconds = jdbc.queryForObject(
                """
                SELECT COALESCE(
                    TIMESTAMPDIFF(MICROSECOND, MIN(created_at), CURRENT_TIMESTAMP(3)) / 1000000.0,
                    0)
                FROM async_outbox
                WHERE status IN ('PENDING', 'PUBLISHING')
                """,
                Double.class);
        return seconds == null ? 0.0 : Math.max(0.0, seconds);
    }

    private static OutboxMessage map(ResultSet result, int rowNumber) throws SQLException {
        return new OutboxMessage(
                result.getLong("id"),
                result.getString("event_id"),
                result.getString("correlation_id"),
                result.getInt("schema_version"),
                result.getString("event_type"),
                result.getString("aggregate_type"),
                result.getString("aggregate_id"),
                result.getString("routing_key"),
                result.getInt("generation"),
                OutboxStatus.valueOf(result.getString("status")),
                result.getInt("publish_attempts"),
                result.getInt("consume_attempts"),
                result.getString("payload_key_id"),
                result.getBytes("payload_nonce"),
                result.getBytes("payload_ciphertext"),
                instant(result.getTimestamp("occurred_at")),
                instant(result.getTimestamp("expires_at")),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")),
                instant(result.getTimestamp("failed_at")),
                result.getString("error_code"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
