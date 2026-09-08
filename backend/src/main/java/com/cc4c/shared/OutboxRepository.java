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

/**
 * 封装共享基础设施持久化、租约与状态更新操作，集中维护数据库语义。
 */
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

    /**
     * 创建 OutboxRepository 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param jdbc 调用方提供的 {@code jdbc} 值
     */
    OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param correlationId 目标对象的稳定标识
     * @param eventType 带版本的异步事件类型
     * @param aggregateType 调用方提供的 {@code aggregateType} 值
     * @param aggregateId 目标对象的稳定标识
     * @param routingKey 调用方提供的 {@code routingKey} 值
     * @param occurredAt 当前操作使用的时间点
     * @param expiresAt 当前操作使用的时间点
     * @param payload 按当前协议处理的业务载荷
     * @param initialStatus 调用方提供的 {@code initialStatus} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     */
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

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param workerId 目标对象的稳定标识
     * @param limit 调用方提供的 {@code limit} 值
     * @param leaseUntil 调用方提供的 {@code leaseUntil} 值
     * @return 按当前方法约定返回结果集合
     */
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     */
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @param nextAttempt 调用方提供的 {@code nextAttempt} 值
     * @param terminal 调用方提供的 {@code terminal} 值
     */
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     */
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     */
    void incrementConsumeAttempt(String eventId, int generation) {
        jdbc.update(
                """
                UPDATE async_outbox SET consume_attempts = consume_attempts + 1
                WHERE event_id = ? AND generation = ?
                """,
                eventId,
                generation);
    }

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     */
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     */
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

    /**
     * 读取所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @return 存在时返回目标值，否则返回空的 Optional
     */
    Optional<OutboxMessage> findByEventId(String eventId) {
        List<OutboxMessage> rows =
                jdbc.query("SELECT " + COLUMNS + " FROM async_outbox WHERE event_id = ?", ROW_MAPPER, eventId);
        return rows.stream().findFirst();
    }

    /**
     * 读取所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param status 当前对象或流程的有限状态
     * @param eventType 带版本的异步事件类型
     * @param query 调用方提供的 {@code query} 值
     * @return 包含分页元数据的查询结果
     */
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

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param message 当前处理的消息或用户提示
     * @param encrypted 调用方提供的 {@code encrypted} 值
     * @param nextGeneration 调用方提供的 {@code nextGeneration} 值
     * @return 按当前规则计算或读取的数值
     */
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

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     * @param actorId 目标对象的稳定标识
     * @return 按当前规则计算或读取的数值
     */
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

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param before 调用方提供的 {@code before} 值
     * @param limit 调用方提供的 {@code limit} 值
     * @return 按当前规则计算或读取的数值
     */
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

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @return 当前操作产生的 Map<String,Long> 结果
     */
    Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS count_value FROM async_outbox GROUP BY status", result -> {
            counts.put(result.getString("status"), result.getLong("count_value"));
        });
        return Map.copyOf(counts);
    }

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @return 按当前规则计算或读取的数值
     */
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

    /**
     * 转换所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param result 调用方提供的 {@code result} 值
     * @param rowNumber 调用方提供的 {@code rowNumber} 值
     * @return 当前操作产生的 OutboxMessage 结果
     * @throws SQLException 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
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

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param value 待处理或存储的值
     * @return 当前操作产生的 Timestamp 结果
     */
    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param value 待处理或存储的值
     * @return 当前操作产生的 Instant 结果
     */
    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
