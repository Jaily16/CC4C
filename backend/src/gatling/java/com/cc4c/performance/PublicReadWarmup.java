package com.cc4c.performance;

import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/** Two-minute warm-up executed immediately before each measured standard round. */
public final class PublicReadWarmup extends Simulation {
    public PublicReadWarmup() {
        setUp(scenario("PublicReadWarmup")
                        .exec(PerformanceSupport.publicRead())
                        .injectClosed(rampConcurrentUsers(1).to(100).during(Duration.ofMinutes(2))))
                .protocols(PerformanceSupport.protocol());
    }
}
