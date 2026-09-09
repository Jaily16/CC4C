package com.cc4c.service;

import com.cc4c.dto.UserSnapshot;
import java.util.Optional;

/** 向其他业务服务提供不含密码的用户资料快照查询。 */
public interface IdentityLookup {
    /**
     * 按用户 ID 查找用于业务展示的最小资料。
     *
     * @param userId 用户 ID
     * @return 用户快照；用户不存在时为空 Optional
     */
    Optional<UserSnapshot> findUser(long userId);
}
