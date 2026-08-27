package com.cc4c.migration;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AExistingDatabaseMigrationTest extends FlywayTestSupport {

    @Test
    void existingTestDatabaseIsExplicitlyBaselinedAndMigrated() throws Exception {
        String url = required("CC4C_TEST_DB_URL");
        String database = databaseName(url);
        assertTrue(database.endsWith("_test"));
        assertFalse(database.endsWith("_flyway_test"));
        assertEquals(16, scalar(url, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """));
        assertEquals(0, scalar(url, """
                SELECT COUNT(*) FROM (
                    SELECT comment_id FROM (
                        SELECT comment_id FROM course_direct_comment
                        UNION ALL SELECT comment_id FROM blog_direct_comment
                        UNION ALL SELECT comment_id FROM indirect_comment
                    ) associations
                    GROUP BY comment_id HAVING COUNT(*) > 1
                ) duplicate_owners
                """));
        assertEquals(0, scalar(url, """
                SELECT COUNT(*) FROM indirect_comment child
                LEFT JOIN comment parent ON parent.comment_id = child.father_id
                WHERE parent.comment_id IS NULL
                """));

        var flyway = flyway(url, true);
        if (scalar(url, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'
                """) == 0) {
            flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(url, required("CC4C_TEST_DB_USERNAME"), required("CC4C_TEST_DB_PASSWORD"))
                    .locations("classpath:db/migration")
                    .baselineVersion(MigrationVersion.fromVersion("1"))
                    .baselineDescription("Existing CC4C schema")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .load();
            flyway.baseline();
        }

        long currentVersion = scalar(url,
                "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)), 0) FROM flyway_schema_history");
        int expectedMigrations = Math.toIntExact(Math.max(0, 4 - currentVersion));
        assertEquals(expectedMigrations, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(4, scalar(url, "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history"));
        assertEquals(255, scalar(url, """
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'password'
                """));
        assertEquals(255, scalar(url, """
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'administrator' AND column_name = 'admin_password'
                """));
    }
}
