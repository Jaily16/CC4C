package com.cc4c.shared;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class AsyncMessageCleanup {
    private final OutboxRepository outbox;
    private final InboxRepository inbox;

    AsyncMessageCleanup(OutboxRepository outbox, InboxRepository inbox) {
        this.outbox = outbox;
        this.inbox = inbox;
    }

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
