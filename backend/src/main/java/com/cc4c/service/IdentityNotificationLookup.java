package com.cc4c.service;

import com.cc4c.dto.NotificationContact;
import java.util.Optional;

/**
 * 定义身份认证跨模块调用所需的稳定能力边界。
 */
public interface IdentityNotificationLookup {
    /**
     * 读取当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param userId 目标对象的稳定标识
     * @return 存在时返回目标值，否则返回空的 Optional
     */
    Optional<NotificationContact> findNotificationContact(long userId);
}
