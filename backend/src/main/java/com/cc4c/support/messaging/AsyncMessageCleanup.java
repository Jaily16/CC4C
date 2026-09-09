package com.cc4c.support.messaging;

import com.cc4c.repository.InboxRepository;
import com.cc4c.repository.OutboxRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时分批清理超过保留期限的已完成消息和消费幂等记录。 */
@Component
final class AsyncMessageCleanup {
    private final OutboxRepository outbox;
    private final InboxRepository inbox;

    /**
     * 接入 Outbox 终态清理及 Inbox 完成记录清理仓库。
     *
     * @param outbox Outbox 持久化仓库
     * @param inbox 消费幂等与租约仓库
     */
    AsyncMessageCleanup(OutboxRepository outbox, InboxRepository inbox) {
        this.outbox = outbox;
        this.inbox = inbox;
    }

    /** 每日 03:15 清理超过 31 天的记录，每轮各表最多 500 条、最多 20 轮；两表均不足一批时结束。 */
    @Scheduled(cron = "0 15 3 * * *")
    void cleanup() {
        Instant before = Instant.now().minus(31, ChronoUnit.DAYS);
        for (int batch = 0; batch < 20; batch++) {
            int outboxDeleted = outbox.cleanupCompleted(before, 500);
            int inboxDeleted = inbox.cleanupDone(before, 500);
            if (outboxDeleted < 500 && inboxDeleted < 500) {
                break;
            }
        }
    }
}
