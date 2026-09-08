package com.cc4c.repository;

import com.cc4c.support.messaging.InboxClaim;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 封装共享基础设施持久化、租约与状态更新操作，集中维护数据库语义。
 */
@Repository
public class InboxRepository {
    private final JdbcTemplate jdbc;

    /**
     * 创建 InboxRepository 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param jdbc 调用方提供的 {@code jdbc} 值
     */
    InboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     * @param workerId 目标对象的稳定标识
     * @param leaseUntil 调用方提供的 {@code leaseUntil} 值
     * @return 当前操作产生的 InboxClaim 结果
     */
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     */
    public void markRetryWaiting(String consumerName, String eventId, int generation, String errorCode) {
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     */
    public void markDone(String consumerName, String eventId, int generation) {
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

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param eventId 异步事件的全局唯一标识
     * @param generation 调用方提供的 {@code generation} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     */
    public void markDead(String consumerName, String eventId, int generation, String errorCode) {
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

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param before 调用方提供的 {@code before} 值
     * @param limit 调用方提供的 {@code limit} 值
     * @return 按当前规则计算或读取的数值
     */
    public int cleanupDone(Instant before, int limit) {
        return jdbc.update(
                """
                DELETE FROM async_inbox
                WHERE status = 'DONE' AND processed_at < ?
                ORDER BY processed_at LIMIT ?
                """,
                Timestamp.from(before),
                limit);
    }

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @return 当前操作产生的 Map<String,Long> 结果
     */
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS count_value FROM async_inbox GROUP BY status", result -> {
            counts.put(result.getString("status"), result.getLong("count_value"));
        });
        return Map.copyOf(counts);
    }

    /**
     * 承载共享基础设施查询返回的一行投影数据。
     *
     * @param status 当前对象或流程的有限状态
     * @param leaseOwner 调用方提供的 {@code leaseOwner} 值
     * @param leaseUntil 调用方提供的 {@code leaseUntil} 值
     */
    private record InboxRow(String status, String leaseOwner, Instant leaseUntil) {}
}
