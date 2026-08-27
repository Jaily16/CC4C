package com.cc4c.migration;

import org.flywaydb.core.Flyway;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

abstract class FlywayTestSupport {

    protected String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is missing: " + name);
        }
        return value;
    }

    protected String databaseName(String jdbcUrl) {
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalStateException("JDBC URL must contain a database name");
        }
        return path.substring(1);
    }

    protected Flyway flyway(String url, boolean cleanDisabled) {
        return Flyway.configure()
                .dataSource(url, required("CC4C_TEST_DB_USERNAME"), required("CC4C_TEST_DB_PASSWORD"))
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(cleanDisabled)
                .load();
    }

    protected long scalar(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url, required("CC4C_TEST_DB_USERNAME"), required("CC4C_TEST_DB_PASSWORD"));
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
