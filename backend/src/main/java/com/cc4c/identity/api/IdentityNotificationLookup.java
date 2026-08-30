package com.cc4c.identity.api;

import java.util.Optional;

/** IdentityNotificationLookup 定义模块之间稳定、可验证的公开契约。 */
public interface IdentityNotificationLookup {
    Optional<NotificationContact> findNotificationContact(long userId);
}
