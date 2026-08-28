package com.cc4c.performance;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

final class PerformanceSupport {
    static final int COURSE_BASE = 1_000_000;
    static final long BLOG_BASE = 7_100_000_000L;

    private PerformanceSupport() {
    }

    static HttpProtocolBuilder protocol() {
        return http.baseUrl(baseUrl())
                .acceptHeader("application/json")
                .contentTypeHeader("application/json")
                .userAgentHeader("CC4C-Aspect6-Gatling/3.15.1")
                .shareConnections()
                .maxConnectionsPerHost(200);
    }

    static ChainBuilder publicRead() {
        return exec(http("course home").get("/courses/home").check(status().is(200)))
                .exec(http("course detail").get("/courses/perf-course-000000").check(status().is(200)))
                .exec(http("course language").get("/courses/language/Java?page=1&size=12").check(status().is(200)))
                .exec(http("course modules").get("/courses/module/1").check(status().is(200)))
                .exec(http("course recommendations").get("/courses/recommend/1/0").check(status().is(200)))
                .exec(http("blog home").get("/blogs/home").check(status().is(200)))
                .exec(http("blog all").get("/blogs/all?page=1&size=12").check(status().is(200)))
                .exec(http("blog language").get("/blogs/list/1?page=1&size=12").check(status().is(200)))
                .exec(http("blog detail").get("/blogs/" + BLOG_BASE).check(status().is(200)))
                .pause(Duration.ofSeconds(1));
    }

    static ChainBuilder csrf() {
        return exec(http("csrf")
                .get("/csrf")
                .check(status().is(200))
                .check(jsonPath("$.data.token").saveAs("csrfToken")));
    }

    static ChainBuilder login() {
        return csrf().exec(http("user login")
                .post("/users/login")
                .header("X-XSRF-TOKEN", "#{csrfToken}")
                .body(io.gatling.javaapi.core.CoreDsl.StringBody(
                        "{\"email\":\"#{email}\",\"password\":\"#{password}\"}"))
                .check(status().is(200)));
    }

    static ChainBuilder authenticatedReads() {
        return exec(http("current user").get("/users/me").check(status().is(200)))
                .exec(http("course favorites").get("/courses/star?page=1&size=8").check(status().is(200)))
                .exec(http("blog favorites").get("/blogs/collect?page=1&size=8").check(status().is(200)))
                .exec(http("my blogs").get("/blogs/myBlogs?page=1&size=10").check(status().is(200)))
                .exec(http("favorite check").get("/courses/star/#{courseId}").check(status().is(200)))
                .exec(http("collected check").get("/blogs/collect/#{blogId}").check(status().is(200)))
                .exec(http("session check").get("/auth/session").check(status().is(200)));
    }

    static ChainBuilder favoriteToggle() {
        return csrf()
                .exec(http("create course favorite")
                        .post("/courses/star/#{courseId}")
                        .header("X-XSRF-TOKEN", "#{csrfToken}")
                        .check(status().is(201)))
                .exec(csrf())
                .exec(http("remove course favorite")
                        .delete("/courses/star/#{courseId}")
                        .header("X-XSRF-TOKEN", "#{csrfToken}")
                        .check(status().is(200)));
    }

    static java.util.Iterator<Map<String, Object>> userFeeder() {
        String password = required("CC4C_PERF_USER_PASSWORD");
        List<Map<String, Object>> users = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            users.add(Map.of(
                    "email", "perf-user-" + index + "@example.invalid",
                    "password", password,
                    "courseId", COURSE_BASE + 900 + index,
                    "blogId", BLOG_BASE + index));
        }
        return users.iterator();
    }

    private static String baseUrl() {
        String raw = required("CC4C_PERF_BASE_URL");
        URI uri = URI.create(raw);
        if (!"http".equalsIgnoreCase(uri.getScheme())
                || !("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))) {
            throw new IllegalStateException("CC4C_PERF_BASE_URL must be loopback HTTP");
        }
        return raw.replaceAll("/+$", "");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required performance variable is missing: " + name);
        }
        return value;
    }
}
