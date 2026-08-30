package com.cc4c.performance;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

public final class PublicReadStandard extends Simulation {
    public PublicReadStandard() {
        setUp(scenario("PublicReadStandard")
                        .during(Duration.ofMinutes(5))
                        .on(PerformanceSupport.publicRead())
                        .injectClosed(constantConcurrentUsers(100).during(Duration.ofMinutes(5))))
                .protocols(PerformanceSupport.protocol());
    }
}
