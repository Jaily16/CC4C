package com.cc4c.service;

import com.cc4c.dto.NotificationContact;
import java.util.Optional;

/** 向消息消费方提供内部通知联系方式查询。 */
public interface IdentityNotificationLookup {
    /**
     * 按用户 ID 获取可用的通知邮箱。
     *
     * @param userId 用户 ID
     * @return 用户及非空邮箱存在时返回联系方式，否则为空 Optional
     */
    Optional<NotificationContact> findNotificationContact(long userId);
}
