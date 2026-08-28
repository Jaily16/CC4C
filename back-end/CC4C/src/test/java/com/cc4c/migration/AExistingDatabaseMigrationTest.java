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
        long tablesBeforeMigration = scalar(url, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """);
        assertTrue(tablesBeforeMigration == 16 || tablesBeforeMigration == 18);
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
        int expectedMigrations = Math.toIntExact(Math.max(0, 6 - currentVersion));
        assertEquals(expectedMigrations, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(6, scalar(url, "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history"));
        assertEquals(18, scalar(url, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """));
        assertEquals(2, scalar(url, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('async_outbox', 'async_inbox')
                """));
        assertEquals(2, scalar(url, """
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND index_name IN (
                    'idx_user_favors_course_user_time_course',
                    'idx_user_collects_blog_user_time_blog')
                """));
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
