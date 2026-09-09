package com.cc4c.security;

import java.util.Optional;

/** 向业务服务提供当前身份及严格区分 USER、ADMIN 的必需身份读取。 */
public interface CurrentActor {
    /**
     * 读取当前已认证的 CC4C 业务身份。
     *
     * @return 业务身份快照，未认证或非业务身份时为空 Optional
     */
    Optional<ActorIdentity> current();

    /**
     * 要求当前身份为 USER，并将其 ID 解析为 long。
     *
     * @return 当前用户 ID
     */
    long requiredUserId();

    /**
     * 要求当前身份为 ADMIN。
     *
     * @return 当前管理员编号
     */
    String requiredAdministratorId();
}
