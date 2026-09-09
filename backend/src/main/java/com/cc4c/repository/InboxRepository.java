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

/** 通过 JDBC 维护消费端幂等记录、处理租约及完成状态。 */
@Repository
public class InboxRepository {
    private final JdbcTemplate jdbc;

    /**
     * 接入消费幂等记录使用的 JDBC 执行器。
     *
     * @param jdbc 使用应用数据源的 JDBC 执行器
     */
    InboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 事务内插入或锁定消费者、事件和代次记录；完成记录不再领取，其他工作者的有效租约阻止抢占。
     *
     * @param consumerName 幂等记录所属消费者名称
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     * @param workerId 领取租约的工作者标识
     * @param leaseUntil 租约到期时间
     * @return 已领取、已完成或其他工作者处理中
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
     * 将指定消费代次置为 RETRY_WAIT，记录错误码并释放租约。
     *
     * @param consumerName 幂等记录所属消费者名称
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     * @param errorCode 不含敏感正文的失败分类码
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
     * 标记消费完成、记录处理时间，清除错误码并释放租约。
     *
     * @param consumerName 幂等记录所属消费者名称
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
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
     * 标记消费终止、记录处理时间和错误码，并释放租约。
     *
     * @param consumerName 幂等记录所属消费者名称
     * @param eventId 异步事件唯一标识
     * @param generation 当前处理的事件代次
     * @param errorCode 不含敏感正文的失败分类码
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
     * 按处理时间删除截止时刻之前的 DONE 记录，最多删除指定条数。
     *
     * @param before 严格早于此时间的记录才可清理
     * @param limit 本次最多处理的记录数量
     * @return 实际删除行数
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
     * 按 Inbox 状态统计记录数量。
     *
     * @return 状态与数量的只读映射
     */
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS count_value FROM async_inbox GROUP BY status", result -> {
            counts.put(result.getString("status"), result.getLong("count_value"));
        });
        return Map.copyOf(counts);
    }

    /**
     * 领取时锁定读取的消费状态与租约信息。
     *
     * @param status 消费状态
     * @param leaseOwner 当前租约持有者标识
     * @param leaseUntil 租约到期时间
     */
    private record InboxRow(String status, String leaseOwner, Instant leaseUntil) {}
}
