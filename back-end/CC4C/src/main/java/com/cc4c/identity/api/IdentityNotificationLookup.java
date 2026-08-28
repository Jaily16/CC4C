package com.cc4c.identity.api;

import java.util.Optional;

public interface IdentityNotificationLookup {
    Optional<NotificationContact> findNotificationContact(long userId);
}
