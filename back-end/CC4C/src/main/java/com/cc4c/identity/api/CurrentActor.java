package com.cc4c.identity.api;

import java.util.Optional;

public interface CurrentActor {
    Optional<ActorIdentity> current();

    long requiredUserId();

    String requiredAdministratorId();
}
