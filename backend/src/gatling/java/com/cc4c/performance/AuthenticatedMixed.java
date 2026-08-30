package com.cc4c.performance;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

public final class AuthenticatedMixed extends Simulation {
    public AuthenticatedMixed() {
        setUp(scenario("AuthenticatedMixed")
                        .feed(PerformanceSupport.userFeeder())
                        .exec(PerformanceSupport.login())
                        .during(Duration.ofMinutes(5))
                        .on(randomSwitch()
                                .on(
                                        percent(20.0).then(PerformanceSupport.publicRead()),
                                        percent(70.0).then(PerformanceSupport.authenticatedReads()),
                                        percent(10.0).then(PerformanceSupport.favoriteToggle())))
                        .injectClosed(constantConcurrentUsers(20).during(Duration.ofMinutes(5))))
                .protocols(PerformanceSupport.protocol())
                .assertions(global().failedRequests().count().is(0L));
    }
}
