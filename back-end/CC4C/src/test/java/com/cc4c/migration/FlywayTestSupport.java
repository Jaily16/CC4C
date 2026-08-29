package com.cc4c.migration;

import com.cc4c.support.Cc4cTestInfrastructure;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

abstract class FlywayTestSupport {

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
                .dataSource(
                        url,
                        Cc4cTestInfrastructure.mysqlUsername(),
                        Cc4cTestInfrastructure.mysqlPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(cleanDisabled)
                .load();
    }

    protected void prepareExistingV1Schema(String schema) throws SQLException {
        Cc4cTestInfrastructure.recreateManagedSchema(schema);
        String url = Cc4cTestInfrastructure.mysqlUrl(schema);
        Flyway.configure()
                .dataSource(
                        url,
                        Cc4cTestInfrastructure.mysqlUsername(),
                        Cc4cTestInfrastructure.mysqlPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .cleanDisabled(true)
                .load()
                .migrate();
        execute(url, "DROP TABLE flyway_schema_history");
    }

    protected long scalar(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url,
                Cc4cTestInfrastructure.mysqlUsername(),
                Cc4cTestInfrastructure.mysqlPassword());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    protected void execute(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url,
                Cc4cTestInfrastructure.mysqlUsername(),
                Cc4cTestInfrastructure.mysqlPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
