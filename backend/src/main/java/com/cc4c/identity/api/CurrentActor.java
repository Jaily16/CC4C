package com.cc4c.identity.api;

import java.util.Optional;

/**
 * CurrentActor 定义身份认证协作方必须实现的稳定契约。
 */
public interface CurrentActor {
    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 存在时返回目标值，否则返回空的 Optional
     */
    Optional<ActorIdentity> current();

    /**
     * 校验当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前规则计算或读取的数值
     */
    long requiredUserId();

    /**
     * 校验当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    String requiredAdministratorId();
}
