package com.cc4c.identity.api;

import java.util.Optional;

public interface IdentityLookup {
    Optional<UserSnapshot> findUser(long userId);
}
