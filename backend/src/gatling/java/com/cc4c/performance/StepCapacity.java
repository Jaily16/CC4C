package com.cc4c.performance;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.group;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

public final class StepCapacity extends Simulation {
    public StepCapacity() {
        setUp(
                        stage("Step50", "capacity-50", 50, 0),
                        stage("Step100", "capacity-100", 100, 2),
                        stage("Step200", "capacity-200", 200, 4),
                        stage("Step500", "capacity-500", 500, 6))
                .protocols(PerformanceSupport.protocol())
                .assertions(
                        details("capacity-50").failedRequests().percent().lte(1.0),
                        details("capacity-100").failedRequests().percent().lte(1.0));
    }

    private io.gatling.javaapi.core.PopulationBuilder stage(
            String scenarioName, String groupName, int concurrency, int delayMinutes) {
        return scenario(scenarioName)
                .during(Duration.ofMinutes(2))
                .on(group(groupName).on(PerformanceSupport.publicRead()))
                .injectClosed(
                        constantConcurrentUsers(0).during(Duration.ofMinutes(delayMinutes)),
                        constantConcurrentUsers(concurrency).during(Duration.ofMinutes(2)));
    }
}
