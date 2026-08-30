package com.cc4c.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class Cc4cTestInfrastructure {
    private static final String MYSQL_USERNAME = "cc4c_test";
    private static final String MYSQL_PASSWORD = "cc4c-test-password";
    private static final String RABBIT_USERNAME = "cc4c_test_user";
    private static final String RABBIT_PASSWORD = "cc4c-test-rabbit-password";
    private static final String RABBIT_VHOST = "cc4c_async_test";
    private static final Pattern MANAGED_SCHEMA = Pattern.compile("^cc4c_[a-z0-9]+_(?:existing_test|flyway_test)$");
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private static final SharedMySqlContainer MYSQL = new SharedMySqlContainer(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("cc4c_test")
            .withUsername(MYSQL_USERNAME)
            .withPassword(MYSQL_PASSWORD)
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+08:00");

    private static final SharedRedisContainer SECURITY_REDIS =
            new SharedRedisContainer(DockerImageName.parse("redis:7.4.10-alpine3.21"));

    private static final SharedRedisContainer CACHE_REDIS =
            new SharedRedisContainer(DockerImageName.parse("redis:7.4.10-alpine3.21"));

    private static final SharedRabbitMqContainer RABBIT = rabbitContainer();

    private static final String RUN_ID =
            UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String EXISTING_SCHEMA = "cc4c_" + RUN_ID + "_existing_test";
    private static final String EMPTY_SCHEMA = "cc4c_" + RUN_ID + "_flyway_test";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> STARTED.set(false), "cc4c-testcontainers-shutdown"));
    }

    @Bean(destroyMethod = "keepRunning")
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        ensureStarted();
        return MYSQL;
    }

    @Bean(destroyMethod = "keepRunning")
    GenericContainer<?> securityRedisContainer() {
        ensureStarted();
        return SECURITY_REDIS;
    }

    @Bean(destroyMethod = "keepRunning")
    GenericContainer<?> cacheRedisContainer() {
        ensureStarted();
        return CACHE_REDIS;
    }

    @Bean(destroyMethod = "keepRunning")
    RabbitMQContainer rabbitMqContainer() {
        ensureStarted();
        return RABBIT;
    }

    public static void ensureStarted() {
        if (STARTED.compareAndSet(false, true)) {
            try {
                Startables.deepStart(Stream.of(MYSQL, SECURITY_REDIS, CACHE_REDIS, RABBIT))
                        .join();
            } catch (RuntimeException exception) {
                STARTED.set(false);
                throw exception;
            }
        }
    }

    private static SharedRabbitMqContainer rabbitContainer() {
        SharedRabbitMqContainer container =
                new SharedRabbitMqContainer(DockerImageName.parse("rabbitmq:4.3.5-management"));
        container.withAdminUser(RABBIT_USERNAME);
        container.withAdminPassword(RABBIT_PASSWORD);
        container.withEnv("RABBITMQ_DEFAULT_USER", RABBIT_USERNAME);
        container.withEnv("RABBITMQ_DEFAULT_PASS", RABBIT_PASSWORD);
        container.withEnv("RABBITMQ_DEFAULT_VHOST", RABBIT_VHOST);
        return container;
    }

    public static String mysqlUrl() {
        ensureStarted();
        return mysqlUrl("cc4c_test");
    }

    public static String mysqlUrl(String schema) {
        ensureStarted();
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/" + schema
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    }

    public static String mysqlUsername() {
        return MYSQL_USERNAME;
    }

    public static String mysqlPassword() {
        return MYSQL_PASSWORD;
    }

    public static String securityRedisUrl() {
        ensureStarted();
        return "redis://" + SECURITY_REDIS.getHost() + ":" + SECURITY_REDIS.getMappedPort(6379);
    }

    public static String cacheRedisUrl() {
        ensureStarted();
        return "redis://" + CACHE_REDIS.getHost() + ":" + CACHE_REDIS.getMappedPort(6379);
    }

    public static String rabbitUrl() {
        ensureStarted();
        return "amqp://" + RABBIT_USERNAME + ":" + RABBIT_PASSWORD + "@" + RABBIT.getHost() + ":"
                + RABBIT.getMappedPort(5672) + "/" + RABBIT_VHOST;
    }

    public static void registerRabbitProperties(DynamicPropertyRegistry registry) {
        ensureStarted();
        registry.add("spring.rabbitmq.addresses", () -> RABBIT.getHost() + ":" + RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> RABBIT_USERNAME);
        registry.add("spring.rabbitmq.password", () -> RABBIT_PASSWORD);
        registry.add("spring.rabbitmq.virtual-host", () -> RABBIT_VHOST);
    }

    public static String existingSchema() {
        return EXISTING_SCHEMA;
    }

    public static String emptySchema() {
        return EMPTY_SCHEMA;
    }

    public static void recreateManagedSchema(String schema) {
        ensureStarted();
        if (!MANAGED_SCHEMA.matcher(schema).matches()
                || (!schema.equals(EXISTING_SCHEMA) && !schema.equals(EMPTY_SCHEMA))) {
            throw new IllegalArgumentException("Refusing to manage an unrecognized test schema");
        }
        try (Connection connection = DriverManager.getConnection(mysqlUrl("cc4c_test"), "root", MYSQL_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            statement.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + MYSQL_USERNAME + "'@'%'");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to prepare an isolated migration schema", exception);
        }
    }

    private static final class SharedMySqlContainer extends MySQLContainer<SharedMySqlContainer> {
        private SharedMySqlContainer(DockerImageName image) {
            super(image);
        }

        public void keepRunning() {
            // Ryuk removes this JVM-scoped container after the Maven process exits.
        }
    }

    private static final class SharedRedisContainer extends GenericContainer<SharedRedisContainer> {
        private SharedRedisContainer(DockerImageName image) {
            super(image);
            withExposedPorts(6379);
            withCommand("redis-server", "--save", "", "--appendonly", "no");
        }

        public void keepRunning() {
            // Ryuk removes this JVM-scoped container after the Maven process exits.
        }
    }

    private static final class SharedRabbitMqContainer extends RabbitMQContainer {
        private SharedRabbitMqContainer(DockerImageName image) {
            super(image);
        }

        @Override
        public String getAmqpUrl() {
            return "amqp://" + RABBIT_USERNAME + ":" + RABBIT_PASSWORD + "@" + getHost() + ":" + getAmqpPort() + "/"
                    + RABBIT_VHOST;
        }

        public void keepRunning() {
            // Ryuk removes this JVM-scoped container after the Maven process exits.
        }
    }
}
