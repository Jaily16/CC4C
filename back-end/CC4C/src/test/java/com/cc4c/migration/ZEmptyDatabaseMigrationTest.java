package com.cc4c.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZEmptyDatabaseMigrationTest extends FlywayTestSupport {

    @Test
    void dedicatedEmptyDatabaseMigratesFromV1AndIsRepeatable() throws Exception {
        String mainUrl = required("CC4C_TEST_DB_URL");
        String url = required("CC4C_TEST_EMPTY_DB_URL");
        String database = databaseName(url);
        assertTrue(database.endsWith("_flyway_test"));
        assertNotEquals(databaseName(mainUrl), database);

        var flyway = flyway(url, false);
        flyway.clean();
        assertEquals(5, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);

        assertEquals(16, scalar(url, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """));
        assertEquals(4, scalar(url, "SELECT COUNT(*) FROM programming_language"));
        assertEquals(61, scalar(url, "SELECT COUNT(*) FROM course"));
        assertEquals(9, scalar(url, "SELECT COUNT(*) FROM course_module"));
        assertEquals(61, scalar(url, "SELECT COUNT(*) FROM module_course"));
        assertEquals(0, scalar(url, "SELECT COUNT(*) FROM administrator"));
        assertEquals(255, scalar(url, """
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'password'
                """));
        assertEquals(2, scalar(url, """
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND index_name IN (
                    'idx_user_favors_course_user_time_course',
                    'idx_user_collects_blog_user_time_blog')
                """));
    }
}
