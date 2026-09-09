package com.cc4c.repository;

import com.cc4c.dto.AsyncMessageSummary;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResult;
import com.cc4c.entity.OutboxMessage;
import com.cc4c.support.messaging.EncryptedMessagePayload;
import com.cc4c.support.messaging.OutboxStatus;
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

/** 通过 JDBC 存储待发事件及加密载荷，管理发布租约、投递状态和人工恢复。 */
@Repository
public class OutboxRepository {
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
     * 接入 Outbox 持久化使用的 JDBC 执行器。
     *
     * @param jdbc 使用应用数据源的 JDBC 执行器
     */
    OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入模式版本 1、代次 0 的事件及加密载荷；初始 DEAD 状态同时记录失败时间。
     *
     * @param eventId 异步事件唯一标识
     * @param correlationId 请求与消息处理关联 ID
     * @param eventType 带版本的事件类型
     * @param aggregateType 关联业务聚合类型
     * @param aggregateId 关联业务聚合标识
     * @param routingKey RabbitMQ 发布路由键
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @param payload 已加密载荷及密钥 ID、nonce
     * @param initialStatus 记录初始投递状态
     * @param errorCode 不含敏感正文的失败分类码
     */
    public void insert(
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
     * 事务内以 SKIP LOCKED 领取到期 PENDING 或租约过期的 PUBLISHING 记录，写入发布租约。
     *
     * @param workerId 领取租约的工作者标识
     * @param limit 本次最多处理的记录数量
     * @param leaseUntil 租约到期时间
     * @return 按数据库 ID 升序返回的已领取事件
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
     * 仅将匹配代次且仍在 PUBLISHING 的事件置为 PUBLISHED，增加发布次数并释放租约。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     */
    public void markPublished(String eventId, int generation) {
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
     * 仅更新匹配代次的 PUBLISHING 记录，增加发布次数；可重试时回到 PENDING，否则置为 PUBLISH_FAILED。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     * @param errorCode 不含敏感正文的失败分类码
     * @param nextAttempt 下一次发布尝试时间
     * @param terminal 是否结束自动发布重试
     */
    public void markPublishFailure(
            String eventId, int generation, String errorCode, Instant nextAttempt, boolean terminal) {
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
     * 将匹配代次且处于 PUBLISHED 或 PUBLISHING 的事件置为 DELIVERED。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     */
    public void markDelivered(String eventId, int generation) {
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
     * 增加指定事件代次的消费尝试次数，不按当前状态筛选。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     */
    public void incrementConsumeAttempt(String eventId, int generation) {
        jdbc.update(
                """
                UPDATE async_outbox SET consume_attempts = consume_attempts + 1
                WHERE event_id = ? AND generation = ?
                """,
                eventId,
                generation);
    }

    /**
     * 将指定事件代次置为 DEAD，并记录失败时间和错误码。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     * @param errorCode 不含敏感正文的失败分类码
     */
    public void markDead(String eventId, int generation, String errorCode) {
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
     * 将指定事件代次置为 EXPIRED，记录 MESSAGE_EXPIRED 及失败时间。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     */
    public void markExpired(String eventId, int generation) {
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
     * 按事件唯一标识读取完整 Outbox 记录。
     *
     * @param eventId 异步事件唯一标识
     * @return 存在时返回记录，否则返回空 Optional
     */
    public Optional<OutboxMessage> findByEventId(String eventId) {
        List<OutboxMessage> rows =
                jdbc.query("SELECT " + COLUMNS + " FROM async_outbox WHERE event_id = ?", ROW_MAPPER, eventId);
        return rows.stream().findFirst();
    }

    /**
     * 按状态和事件类型分页查询消息摘要；未指定状态时仅列出待处理、发布中及失败状态。
     *
     * @param status 可空的 Outbox 状态筛选
     * @param eventType 可空的事件类型筛选
     * @param query 从 1 起算的分页条件
     * @return 按创建时间和 ID 倒序排列的消息摘要页
     */
    public PageResult<AsyncMessageSummary> findPage(OutboxStatus status, String eventType, PageQuery query) {
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
     * 仅对匹配旧代次的 PUBLISH_FAILED 或 DEAD 事件更新代次和密文，重置计数与历史结果并回到 PENDING。
     *
     * @param message 包含待恢复事件 ID 和旧代次的记录
     * @param encrypted 针对新代次生成的加密载荷
     * @param nextGeneration 人工恢复后使用的新代次
     * @return 成功更新为 1；状态或代次已变化时为 0
     */
    public int resetForManualRetry(OutboxMessage message, EncryptedMessagePayload encrypted, int nextGeneration) {
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
     * 仅将匹配代次的 PUBLISH_FAILED 或 DEAD 事件置为 IGNORED，并记录操作者和忽略时间。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     * @param actorId 执行忽略操作的管理员 ID
     * @return 成功更新为 1；状态或代次已变化时为 0
     */
    public int ignore(String eventId, int generation, String actorId) {
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
     * 按 ID 限量删除更新时间早于截止时刻的 DELIVERED、EXPIRED 或 IGNORED 记录。
     *
     * @param before 严格早于此时间的记录才可清理
     * @param limit 本次最多处理的记录数量
     * @return 实际删除行数
     */
    public int cleanupCompleted(Instant before, int limit) {
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
     * 按 Outbox 状态统计记录数量。
     *
     * @return 状态与数量的只读映射
     */
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS count_value FROM async_outbox GROUP BY status", result -> {
            counts.put(result.getString("status"), result.getLong("count_value"));
        });
        return Map.copyOf(counts);
    }

    /**
     * 计算 PENDING 或 PUBLISHING 记录中最早创建时间距今的秒数。
     *
     * @return 非负等待秒数，无匹配记录时为零
     */
    public double oldestPendingSeconds() {
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
     * 将 JDBC 行字段转换为 Outbox 快照，读取载荷原始字节并转换可空时间字段。
     *
     * @param result 已定位到当前行的 JDBC 结果集
     * @param rowNumber RowMapper 回调行号，本映射不使用该值
     * @return 完整的 Outbox 记录
     * @throws SQLException 读取 JDBC 列值失败时抛出
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
     * 将可空 Instant 转换为 JDBC Timestamp。
     *
     * @param value 可空的待转换时间
     * @return 对应时间戳；输入为空时仍为空
     */
    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    /**
     * 将可空 JDBC Timestamp 转换为 Instant。
     *
     * @param value 可空的待转换时间
     * @return 对应时间点；输入为空时仍为空
     */
    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
