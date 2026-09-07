package com.cc4c.shared;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AsyncMessageCleanup 负责共享基础设施的一项明确运行职责，并保持现有外部行为不变。
 */
@Component
final class AsyncMessageCleanup {
    private final OutboxRepository outbox;
    private final InboxRepository inbox;

    /**
     * 创建 AsyncMessageCleanup 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param outbox 由容器注入的 OutboxRepository 协作组件
     * @param inbox 由容器注入的 InboxRepository 协作组件
     */
    AsyncMessageCleanup(OutboxRepository outbox, InboxRepository inbox) {
        this.outbox = outbox;
        this.inbox = inbox;
    }

    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     */
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
