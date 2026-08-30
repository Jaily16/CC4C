package com.cc4c.identity.api;

import java.util.Optional;

/** IdentityLookup 定义模块之间稳定、可验证的公开契约。 */
public interface IdentityLookup {
    Optional<UserSnapshot> findUser(long userId);
}
