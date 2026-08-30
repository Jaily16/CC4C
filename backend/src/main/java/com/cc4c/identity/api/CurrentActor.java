package com.cc4c.identity.api;

import java.util.Optional;

/** CurrentActor 定义模块之间稳定、可验证的公开契约。 */
public interface CurrentActor {
    Optional<ActorIdentity> current();

    long requiredUserId();

    String requiredAdministratorId();
}
