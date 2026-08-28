package com.cc4c.performance;

import com.cc4c.CC4CApplication;
import com.cc4c.shared.BusinessCache;
import com.cc4c.shared.BusinessCacheMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Aspect4BenchmarkApplication {
    private static final int CONCURRENCY = 16;
    private static final int REQUESTS_PER_ROUND = 3_000;
    private static final int ROUND_COUNT = 3;
    private static final double MAX_COLD_REGRESSION = 1.15;
    private static final double MAX_P99_REGRESSION = 1.15;

    private Aspect4BenchmarkApplication() {
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        BenchmarkEnvironment environment = BenchmarkEnvironment.load();
        List<String> targets = loadTargets(environment);
        String previousConfigName = System.getProperty("spring.config.name");
        System.setProperty("spring.config.name", "cc4c-aspect4-benchmark");
        BenchmarkResult result;
        try {
            ModeResult baseline = runMode(environment, targets, false);
            ModeResult cached = runMode(environment, targets, true);
            result = gate(baseline, cached);
            writeResult(result);
        } finally {
            if (previousConfigName == null) {
                System.clearProperty("spring.config.name");
            } else {
                System.setProperty("spring.config.name", previousConfigName);
            }
        }
        if (!result.passed()) {
            throw new IllegalStateException("Aspect four performance gate failed: "
                    + String.join(", ", result.failures()));
        }
        System.out.println("Aspect4 performance gate passed; report written under ignored temp directory.");
    }

    private static ModeResult runMode(
            BenchmarkEnvironment environment, List<String> targets, boolean cacheEnabled) throws Exception {
        String runId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> properties = applicationProperties(environment, cacheEnabled, runId);
        SpringApplication application = new SpringApplication(
                CC4CApplication.class, BenchmarkConfiguration.class);
        application.setDefaultProperties(properties);
        try (ConfigurableApplicationContext context = application.run()) {
            int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            BusinessCache cache = context.getBean(BusinessCache.class);
            BenchmarkQueryCounter queryCounter = context.getBean(BenchmarkQueryCounter.class);
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);

            if (cacheEnabled) {
                cache.clearNamespaceForTests();
            }
            LatencySummary cold = coldSummary(client, port, targets, cache, cacheEnabled);

            if (cacheEnabled) {
                cache.clearNamespaceForTests();
                for (int repeat = 0; repeat < 5; repeat++) {
                    runSequential(client, port, targets);
                }
            }
            cache.metrics().reset();
            queryCounter.reset();

            List<RoundResult> rounds = new ArrayList<>();
            for (int round = 0; round < ROUND_COUNT; round++) {
                rounds.add(runRound(client, port, targets, REQUESTS_PER_ROUND));
            }
            RoundResult median = medianRound(rounds);
            long selects = queryCounter.count();
            BusinessCacheMetrics.Snapshot cacheMetrics = cache.metrics().snapshot();
            PoolSnapshot pool = poolSnapshot(dataSource);
            if (cacheEnabled) {
                cache.clearNamespaceForTests();
            }
            return new ModeResult(cold, median, selects, cacheMetrics, pool, rounds);
        }
    }

    private static LatencySummary coldSummary(
            HttpClient client,
            int port,
            List<String> targets,
            BusinessCache cache,
            boolean cacheEnabled) throws Exception {
        List<Long> latencies = new ArrayList<>();
        int errors = 0;
        for (int cycle = 0; cycle < 10; cycle++) {
            if (cacheEnabled) {
                cache.clearNamespaceForTests();
            }
            RequestBatch batch = runSequential(client, port, targets);
            Arrays.stream(batch.latenciesNanos()).forEach(latencies::add);
            errors += batch.errors();
        }
        return summarize(latencies.stream().mapToLong(Long::longValue).toArray(), errors, 0);
    }

    private static RequestBatch runSequential(HttpClient client, int port, List<String> targets)
            throws Exception {
        long[] latencies = new long[targets.size()];
        int errors = 0;
        for (int index = 0; index < targets.size(); index++) {
            RequestResult result = request(client, port, targets.get(index));
            latencies[index] = result.durationNanos();
            if (!result.success()) {
                errors++;
            }
        }
        return new RequestBatch(latencies, errors);
    }

    private static RoundResult runRound(
            HttpClient client, int port, List<String> targets, int requestCount) throws Exception {
        long[] latencies = new long[requestCount];
        AtomicInteger errors = new AtomicInteger();
        Semaphore concurrency = new Semaphore(CONCURRENCY);
        long started = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> requests = new ArrayList<>(requestCount);
            for (int index = 0; index < requestCount; index++) {
                int requestIndex = index;
                requests.add(CompletableFuture.runAsync(() -> {
                    try {
                        concurrency.acquire();
                        try {
                            RequestResult result = request(
                                    client, port, targets.get(requestIndex % targets.size()));
                            latencies[requestIndex] = result.durationNanos();
                            if (!result.success()) {
                                errors.incrementAndGet();
                            }
                        } finally {
                            concurrency.release();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        errors.incrementAndGet();
                    } catch (Exception exception) {
                        errors.incrementAndGet();
                    }
                }, executor));
            }
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
        }
        long elapsed = System.nanoTime() - started;
        LatencySummary summary = summarize(latencies, errors.get(), elapsed);
        return new RoundResult(
                summary.requests(), summary.errors(), summary.p50Millis(), summary.p95Millis(),
                summary.p99Millis(), summary.throughputPerSecond());
    }

    private static RequestResult request(HttpClient client, int port, String target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + target))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        long started = System.nanoTime();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        return new RequestResult(
                System.nanoTime() - started,
                response.statusCode() >= 200 && response.statusCode() < 300);
    }

    private static LatencySummary summarize(long[] source, int errors, long elapsedNanos) {
        long[] sorted = Arrays.stream(source).filter(value -> value > 0).sorted().toArray();
        if (sorted.length == 0) {
            return new LatencySummary(0, errors, 0, 0, 0, 0);
        }
        double throughput = elapsedNanos <= 0 ? 0.0
                : sorted.length / (elapsedNanos / 1_000_000_000.0);
        return new LatencySummary(
                sorted.length,
                errors,
                millis(percentile(sorted, 0.50)),
                millis(percentile(sorted, 0.95)),
                millis(percentile(sorted, 0.99)),
                throughput);
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static RoundResult medianRound(List<RoundResult> rounds) {
        return new RoundResult(
                medianLong(rounds.stream().mapToLong(RoundResult::requests).toArray()),
                medianLong(rounds.stream().mapToLong(RoundResult::errors).toArray()),
                medianDouble(rounds.stream().mapToDouble(RoundResult::p50Millis).toArray()),
                medianDouble(rounds.stream().mapToDouble(RoundResult::p95Millis).toArray()),
                medianDouble(rounds.stream().mapToDouble(RoundResult::p99Millis).toArray()),
                medianDouble(rounds.stream().mapToDouble(RoundResult::throughputPerSecond).toArray()));
    }

    private static long medianLong(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private static double medianDouble(double[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private static BenchmarkResult gate(ModeResult baseline, ModeResult cached) {
        List<String> failures = new ArrayList<>();
        if (baseline.hot().errors() != 0 || cached.hot().errors() != 0
                || baseline.cold().errors() != 0 || cached.cold().errors() != 0) {
            failures.add("HTTP errors were observed");
        }
        double hitRatio = cached.cacheMetrics().positiveHitRatio();
        if (hitRatio < 0.85) {
            failures.add("positive cache hit ratio below 85%");
        }
        double selectReduction = baseline.selects() == 0 ? 0.0
                : 1.0 - (double) cached.selects() / baseline.selects();
        if (selectReduction < 0.80) {
            failures.add("SELECT reduction below 80%");
        }
        double p95Improvement = baseline.hot().p95Millis() == 0 ? 0.0
                : 1.0 - cached.hot().p95Millis() / baseline.hot().p95Millis();
        if (p95Improvement < 0.30) {
            failures.add("hot p95 improvement below 30%");
        }
        if (cached.cold().p95Millis() > baseline.cold().p95Millis() * MAX_COLD_REGRESSION) {
            failures.add("cold p95 regressed by more than 15%");
        }
        if (cached.hot().p99Millis() > baseline.hot().p99Millis() * MAX_P99_REGRESSION) {
            failures.add("hot p99 regressed by more than 15%");
        }
        return new BenchmarkResult(
                failures.isEmpty(),
                List.copyOf(failures),
                hitRatio,
                selectReduction,
                p95Improvement,
                baseline,
                cached);
    }

    private static PoolSnapshot poolSnapshot(HikariDataSource dataSource) {
        var bean = dataSource.getHikariPoolMXBean();
        return new PoolSnapshot(
                bean == null ? 0 : bean.getActiveConnections(),
                bean == null ? 0 : bean.getIdleConnections(),
                bean == null ? 0 : bean.getThreadsAwaitingConnection(),
                bean == null ? 0 : bean.getTotalConnections());
    }

    private static void writeResult(BenchmarkResult result) throws Exception {
        Path outputDirectory = Path.of(System.getProperty("user.dir"))
                .resolve("../../temp")
                .normalize()
                .toAbsolutePath();
        Files.createDirectories(outputDirectory);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Files.writeString(
                outputDirectory.resolve("cc4c-v3-aspect4-benchmark.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result),
                StandardCharsets.UTF_8);
        String markdown = """
                # CC4C V3 Aspect 4 Benchmark

                - Passed: %s
                - Cache hit ratio: %.2f%%
                - SELECT reduction: %.2f%%
                - Hot p95 improvement: %.2f%%
                - Baseline p95/p99: %.3f / %.3f ms
                - Cached p95/p99: %.3f / %.3f ms
                - Failures: %s
                """.formatted(
                result.passed(),
                result.cacheHitRatio() * 100,
                result.selectReduction() * 100,
                result.p95Improvement() * 100,
                result.baseline().hot().p95Millis(),
                result.baseline().hot().p99Millis(),
                result.cached().hot().p95Millis(),
                result.cached().hot().p99Millis(),
                result.failures().isEmpty() ? "none" : String.join("; ", result.failures()));
        Files.writeString(
                outputDirectory.resolve("cc4c-v3-aspect4-benchmark.md"),
                markdown,
                StandardCharsets.UTF_8);
    }

    private static List<String> loadTargets(BenchmarkEnvironment environment) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                environment.databaseUrl(), environment.databaseUsername(), environment.databasePassword());
                Statement statement = connection.createStatement()) {
            int languageId;
            String languageName;
            try (ResultSet result = statement.executeQuery("""
                    SELECT language_id, language_name FROM programming_language
                    WHERE deleted = 0 ORDER BY language_id LIMIT 1
                    """)) {
                result.next();
                languageId = result.getInt(1);
                languageName = result.getString(2);
            }
            String courseName;
            try (ResultSet result = statement.executeQuery("""
                    SELECT course_name FROM course
                    WHERE course_id >= 1000000 AND deleted = 0 ORDER BY course_id LIMIT 1
                    """)) {
                result.next();
                courseName = result.getString(1);
            }
            long blogId;
            try (ResultSet result = statement.executeQuery("""
                    SELECT blog_id FROM blog
                    WHERE blog_id >= 7100000000 AND state = 1 AND deleted = 0
                    ORDER BY blog_id LIMIT 1
                    """)) {
                result.next();
                blogId = result.getLong(1);
            }
            return List.of(
                    "/courses/home?page=1&size=20",
                    "/courses/language/" + encode(languageName) + "?page=1&size=20",
                    "/courses/" + encode(courseName),
                    "/courses/module/" + languageId,
                    "/courses/recommend/" + languageId + "/0",
                    "/blogs/home?page=1&size=20",
                    "/blogs/all?page=1&size=20",
                    "/blogs/list/" + languageId + "?page=1&size=20",
                    "/blogs/" + blogId);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Map<String, Object> applicationProperties(
            BenchmarkEnvironment environment, boolean cacheEnabled, String runId) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.application.name", "CC4C-Aspect4-Benchmark");
        properties.put("server.port", "0");
        properties.put("spring.datasource.url", environment.databaseUrl());
        properties.put("spring.datasource.username", environment.databaseUsername());
        properties.put("spring.datasource.password", environment.databasePassword());
        properties.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        properties.put("spring.datasource.hikari.connection-timeout", "3000");
        properties.put("spring.datasource.hikari.validation-timeout", "1000");
        properties.put("spring.flyway.baseline-on-migrate", "false");
        properties.put("spring.flyway.locations", "classpath:db/migration");
        properties.put("spring.flyway.validate-on-migrate", "true");
        properties.put("spring.data.redis.url", environment.redisUrl());
        properties.put("spring.data.redis.connect-timeout", "2s");
        properties.put("spring.data.redis.timeout", "2s");
        properties.put("spring.session.store-type", "redis");
        properties.put("spring.session.timeout", "2h");
        properties.put("spring.session.redis.repository-type", "indexed");
        properties.put("spring.session.redis.namespace", "cc4c:perf:" + runId + ":session");
        properties.put("spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration");
        properties.put("spring.mail.host", "127.0.0.1");
        properties.put("spring.mail.port", "2525");
        properties.put("spring.mail.username", "benchmark@example.invalid");
        properties.put("spring.mail.password", "benchmark-not-used");
        properties.put("spring.mail.properties.mail.smtp.auth", "false");
        properties.put("spring.rabbitmq.dynamic", "false");
        properties.put("spring.rabbitmq.listener.simple.auto-startup", "false");
        properties.put("springdoc.api-docs.enabled", "false");
        properties.put("springdoc.swagger-ui.enabled", "false");
        properties.put("management.server.port", "-1");
        properties.put("cc4c.observability.enabled", "false");
        properties.put("cc4c.observability.environment", "performance");
        properties.put("cc4c.observability.management-username", "benchmark-observer");
        properties.put("cc4c.observability.management-password",
                "benchmark-observer-password-not-a-secret");
        properties.put("cc4c.observability.messaging-sample-interval", "15s");
        properties.put("cc4c.observability.max-http-uri-tags", "100");
        properties.put("cc4c.security.pepper", "cc4c-aspect-four-benchmark-pepper-not-a-secret");
        properties.put("cc4c.security.cookie-secure", "false");
        properties.put("cc4c.security.allowed-origins", "http://localhost:5173");
        properties.put("cc4c.security.bcrypt-strength", "4");
        properties.put("cc4c.security.password-readiness-enabled", "false");
        properties.put("cc4c.security.redis-readiness-enabled", "true");
        properties.put("cc4c.security.key-prefix", "cc4c:perf:" + runId + ":security");
        properties.put("cc4c.cache.enabled", Boolean.toString(cacheEnabled));
        properties.put("cc4c.cache.redis-url", environment.redisUrl());
        properties.put("cc4c.cache.namespace", "cc4c:perf:" + runId + ":cache");
        properties.put("cc4c.cache.test-cleanup-enabled", "true");
        properties.put("cc4c.messaging.namespace", "cc4c.perf.messaging." + runId);
        properties.put("cc4c.messaging.active-key-id", "benchmark-v1");
        properties.put(
                "cc4c.messaging.payload-keys",
                "benchmark-v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        properties.put("cc4c.messaging.moderation-recipients", "benchmark@example.invalid");
        properties.put("cc4c.messaging.confirm-timeout", "1s");
        properties.put("cc4c.messaging.consumer-retry-delays", "1s,2s,3s");
        properties.put("cc4c.messaging.poll-interval", "1h");
        properties.put("cc4c.messaging.dispatcher-enabled", "false");
        properties.put("cc4c.messaging.consumers-enabled", "false");
        properties.put("cc4c.test-controller-enabled", "false");
        properties.put("cc4c.save-img-path", "target/performance-files/blog/");
        properties.put("cc4c.request-img-path", "http://localhost/performance-blog/");
        properties.put("cc4c.save-avatar-path", "target/performance-files/avatar/");
        properties.put("cc4c.request-avatar-path", "http://localhost/performance-avatar/");
        properties.put("cc4c.save-avatar-path", "target/performance-files/avatar/");
        properties.put("cc4c.request-avatar-path", "http://localhost/performance-avatar/");
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    static class BenchmarkConfiguration {
        @Bean
        BenchmarkQueryCounter benchmarkQueryCounter() {
            return new BenchmarkQueryCounter();
        }

        @Bean
        Interceptor benchmarkQueryInterceptor(BenchmarkQueryCounter counter) {
            return new BenchmarkQueryInterceptor(counter);
        }
    }

    @Intercepts({
            @Signature(type = Executor.class, method = "query", args = {
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
            @Signature(type = Executor.class, method = "query", args = {
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                    org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class})
    })
    static final class BenchmarkQueryInterceptor implements Interceptor {
        private final BenchmarkQueryCounter counter;

        BenchmarkQueryInterceptor(BenchmarkQueryCounter counter) {
            this.counter = counter;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if (statement.getSqlCommandType() == SqlCommandType.SELECT) {
                counter.increment();
            }
            return invocation.proceed();
        }
    }

    static final class BenchmarkQueryCounter {
        private final AtomicLong value = new AtomicLong();

        void increment() {
            value.incrementAndGet();
        }

        void reset() {
            value.set(0);
        }

        long count() {
            return value.get();
        }
    }

    private record BenchmarkEnvironment(
            String databaseUrl,
            String databaseUsername,
            String databasePassword,
            String redisUrl
    ) {
        static BenchmarkEnvironment load() {
            String url = required("CC4C_PERF_DB_URL");
            String confirmation = required("CC4C_PERF_DB_RESET_CONFIRM");
            URI databaseUri = URI.create(url.substring("jdbc:".length()));
            String path = databaseUri.getPath();
            String database = path == null || path.length() <= 1 ? "" : path.substring(1);
            if (!database.endsWith("_perf_test") || !database.equals(confirmation)) {
                throw new IllegalStateException("Performance database safety check failed");
            }
            return new BenchmarkEnvironment(
                    url,
                    required("CC4C_PERF_DB_USERNAME"),
                    required("CC4C_PERF_DB_PASSWORD"),
                    required("CC4C_PERF_CACHE_REDIS_URL"));
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required performance environment variable is missing: " + name);
            }
            return value;
        }
    }

    private record RequestResult(long durationNanos, boolean success) {
    }

    private record RequestBatch(long[] latenciesNanos, int errors) {
    }

    private record LatencySummary(
            long requests,
            long errors,
            double p50Millis,
            double p95Millis,
            double p99Millis,
            double throughputPerSecond
    ) {
    }

    private record RoundResult(
            long requests,
            long errors,
            double p50Millis,
            double p95Millis,
            double p99Millis,
            double throughputPerSecond
    ) {
    }

    private record PoolSnapshot(int active, int idle, int pending, int total) {
    }

    private record ModeResult(
            LatencySummary cold,
            RoundResult hot,
            long selects,
            BusinessCacheMetrics.Snapshot cacheMetrics,
            PoolSnapshot pool,
            List<RoundResult> rounds
    ) {
    }

    private record BenchmarkResult(
            boolean passed,
            List<String> failures,
            double cacheHitRatio,
            double selectReduction,
            double p95Improvement,
            ModeResult baseline,
            ModeResult cached
    ) {
    }
}
