package com.cc4c.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;

public final class PublicReadSmoke extends Simulation {
    private final ScenarioBuilder scenario = scenario("PublicReadSmoke")
            .during(Duration.ofMinutes(1)).on(PerformanceSupport.publicRead());

    public PublicReadSmoke() {
        setUp(scenario.injectClosed(
                        constantConcurrentUsers(20).during(Duration.ofMinutes(1))))
                .protocols(PerformanceSupport.protocol())
                .assertions(
                        global().failedRequests().count().is(0L),
                        global().responseTime().percentile(95.0).lte(500),
                        global().responseTime().percentile(99.0).lte(1_000));
    }
}
