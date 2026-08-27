package com.cc4c.performance;

import org.flywaydb.core.Flyway;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class Aspect4PerformanceDataSeeder {
    private static final int USER_COUNT = 2_000;
    private static final int COURSE_COUNT = 1_000;
    private static final int BLOG_COUNT = 20_000;
    private static final int COURSE_FAVORITE_COUNT = 50_000;
    private static final int BLOG_FAVORITE_COUNT = 50_000;
    private static final int TOP_COMMENT_COUNT = 50_000;
    private static final int REPLY_COUNT = 50_000;
    private static final long USER_BASE = 7_000_000_000L;
    private static final long USER_LIMIT = USER_BASE + USER_COUNT;
    private static final int COURSE_BASE = 1_000_000;
    private static final int COURSE_LIMIT = COURSE_BASE + COURSE_COUNT;
    private static final long BLOG_BASE = 7_100_000_000L;
    private static final long BLOG_LIMIT = BLOG_BASE + BLOG_COUNT;
    private static final long COMMENT_BASE = 7_200_000_000L;
    private static final long COMMENT_LIMIT = COMMENT_BASE + TOP_COMMENT_COUNT + REPLY_COUNT;
    private static final long RANDOM_SEED = 20_260_827L;
    private static final int BATCH_SIZE = 1_000;

    private Aspect4PerformanceDataSeeder() {
    }

    public static void main(String[] args) throws Exception {
        Environment environment = Environment.load();
        Flyway flyway = Flyway.configure()
                .dataSource(environment.url(), environment.username(), environment.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
        flyway.migrate();
        if (!flyway.validateWithResult().validationSuccessful) {
            throw new IllegalStateException("Performance database Flyway validation failed");
        }

        try (Connection connection = DriverManager.getConnection(
                environment.url(), environment.username(), environment.password())) {
            connection.setAutoCommit(false);
            try {
                cleanupReservedRanges(connection);
                Map<Integer, String> languages = loadLanguages(connection);
                if (languages.size() < 4) {
                    throw new IllegalStateException("Performance database must contain the four catalog languages");
                }
                seedUsers(connection, languages);
                seedCourses(connection, languages);
                seedBlogs(connection);
                seedFavorites(connection);
                seedComments(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
        System.out.printf(
                "Aspect4 performance data ready: users=%d courses=%d blogs=%d relationshipsAndComments=%d%n",
                USER_COUNT,
                COURSE_COUNT,
                BLOG_COUNT,
                COURSE_FAVORITE_COUNT + BLOG_FAVORITE_COUNT + TOP_COMMENT_COUNT + REPLY_COUNT);
    }

    private static void cleanupReservedRanges(Connection connection) throws SQLException {
        execute(connection, "DELETE FROM indirect_comment WHERE "
                + range("comment_id", COMMENT_BASE, COMMENT_LIMIT)
                + " OR " + range("father_id", COMMENT_BASE, COMMENT_LIMIT));
        execute(connection, "DELETE FROM course_direct_comment WHERE "
                + range("comment_id", COMMENT_BASE, COMMENT_LIMIT));
        execute(connection, "DELETE FROM blog_direct_comment WHERE "
                + range("comment_id", COMMENT_BASE, COMMENT_LIMIT));
        execute(connection, "DELETE FROM comment WHERE "
                + range("comment_id", COMMENT_BASE, COMMENT_LIMIT));
        execute(connection, "DELETE FROM user_collects_blog WHERE "
                + range("user_id", USER_BASE, USER_LIMIT)
                + " OR " + range("blog_id", BLOG_BASE, BLOG_LIMIT));
        execute(connection, "DELETE FROM user_favors_course WHERE "
                + range("user_id", USER_BASE, USER_LIMIT)
                + " OR " + range("course_id", COURSE_BASE, COURSE_LIMIT));
        execute(connection, "DELETE FROM blog_involves_language WHERE "
                + range("blog_id", BLOG_BASE, BLOG_LIMIT));
        execute(connection, "DELETE FROM user_submits_blog WHERE "
                + range("blog_id", BLOG_BASE, BLOG_LIMIT)
                + " OR " + range("user_id", USER_BASE, USER_LIMIT));
        execute(connection, "DELETE FROM blog WHERE " + range("blog_id", BLOG_BASE, BLOG_LIMIT));
        execute(connection, "DELETE FROM module_course WHERE "
                + range("course_id", COURSE_BASE, COURSE_LIMIT));
        execute(connection, "DELETE FROM course WHERE " + range("course_id", COURSE_BASE, COURSE_LIMIT));
        execute(connection, "DELETE FROM user WHERE " + range("user_id", USER_BASE, USER_LIMIT));
    }

    private static String range(String column, long startInclusive, long endExclusive) {
        return "(" + column + " >= " + startInclusive + " AND " + column + " < " + endExclusive + ")";
    }

    private static Map<Integer, String> loadLanguages(Connection connection) throws SQLException {
        Map<Integer, String> result = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT language_id, language_name
                        FROM programming_language
                        WHERE deleted = 0
                        ORDER BY language_id
                        """)) {
            while (rows.next()) {
                result.put(rows.getInt(1), rows.getString(2));
            }
        }
        return result;
    }

    private static void seedUsers(Connection connection, Map<Integer, String> languages) throws SQLException {
        String password = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("benchmark-only-password");
        List<Integer> languageIds = languages.keySet().stream().sorted().toList();
        SplittableRandom random = new SplittableRandom(RANDOM_SEED);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO user(
                    user_id, user_name, email, password, major, avatar, state,
                    create_time, favourite_language, deleted)
                VALUES(?, ?, ?, ?, ?, NULL, 0, ?, ?, 0)
                """)) {
            for (int index = 0; index < USER_COUNT; index++) {
                statement.setLong(1, USER_BASE + index);
                statement.setString(2, "perf-user-" + index);
                statement.setString(3, "perf-user-" + index + "@example.invalid");
                statement.setString(4, password);
                statement.setInt(5, random.nextInt(-1, 2));
                statement.setTimestamp(6, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")
                        .plus(index, ChronoUnit.MINUTES)));
                statement.setInt(7, languageIds.get(random.nextInt(languageIds.size())));
                addBatch(statement, index);
            }
            statement.executeBatch();
        }
    }

    private static void seedCourses(Connection connection, Map<Integer, String> languages) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO course(
                    course_id, language_name, course_name, description, level, state, deleted)
                VALUES(?, ?, ?, ?, ?, 1, 0)
                """)) {
            for (int index = 0; index < COURSE_COUNT; index++) {
                int languageId = (index % 4) + 1;
                statement.setInt(1, COURSE_BASE + index);
                statement.setString(2, languages.get(languageId));
                statement.setString(3, "perf-course-" + String.format("%06d", index));
                statement.setString(4, "Deterministic aspect four performance course " + index);
                statement.setInt(5, (index % 5) - 2);
                addBatch(statement, index);
            }
            statement.executeBatch();
        }
    }

    private static void seedBlogs(Connection connection) throws SQLException {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        try (PreparedStatement blog = connection.prepareStatement("""
                    INSERT INTO blog(
                        blog_id, writer_id, title, content, publish_time, click, state, deleted)
                    VALUES(?, ?, ?, ?, ?, ?, ?, 0)
                    """);
                PreparedStatement language = connection.prepareStatement(
                        "INSERT INTO blog_involves_language(blog_id, language_id) VALUES(?, ?)");
                PreparedStatement submission = connection.prepareStatement("""
                        INSERT INTO user_submits_blog(user_id, blog_id, submit_time) VALUES(?, ?, ?)
                        """)) {
            for (int index = 0; index < BLOG_COUNT; index++) {
                long blogId = BLOG_BASE + index;
                long writerId = USER_BASE + (index % USER_COUNT);
                Timestamp published = Timestamp.from(base.plus(index, ChronoUnit.MINUTES));
                int state = index % 20 < 17 ? 1 : index % 20 == 17 ? 0 : -1;
                blog.setLong(1, blogId);
                blog.setLong(2, writerId);
                blog.setString(3, "perf-blog-" + String.format("%06d", index));
                blog.setString(4, "Deterministic aspect four performance blog content " + index);
                blog.setTimestamp(5, published);
                blog.setInt(6, (index * 37) % 100_000);
                blog.setInt(7, state);
                language.setLong(1, blogId);
                language.setInt(2, (index % 4) + 1);
                submission.setLong(1, writerId);
                submission.setLong(2, blogId);
                submission.setTimestamp(3, published);
                addBatch(blog, index);
                addBatch(language, index);
                addBatch(submission, index);
            }
            blog.executeBatch();
            language.executeBatch();
            submission.executeBatch();
        }
    }

    private static void seedFavorites(Connection connection) throws SQLException {
        Instant base = Instant.parse("2026-07-01T00:00:00Z");
        try (PreparedStatement courseFavorite = connection.prepareStatement("""
                    INSERT INTO user_favors_course(user_id, course_id, time) VALUES(?, ?, ?)
                    """);
                PreparedStatement blogFavorite = connection.prepareStatement("""
                    INSERT INTO user_collects_blog(user_id, blog_id, time) VALUES(?, ?, ?)
                    """)) {
            for (int index = 0; index < COURSE_FAVORITE_COUNT; index++) {
                int userIndex = index % USER_COUNT;
                int round = index / USER_COUNT;
                courseFavorite.setLong(1, USER_BASE + userIndex);
                courseFavorite.setInt(2, COURSE_BASE + ((userIndex * 37 + round * 41) % COURSE_COUNT));
                courseFavorite.setTimestamp(3, Timestamp.from(base.plus(index, ChronoUnit.SECONDS)));

                int blogIndex = (userIndex * 101 + round * 43) % BLOG_COUNT;
                while (blogIndex % 20 >= 17) {
                    blogIndex = (blogIndex + 1) % BLOG_COUNT;
                }
                blogFavorite.setLong(1, USER_BASE + userIndex);
                blogFavorite.setLong(2, BLOG_BASE + blogIndex);
                blogFavorite.setTimestamp(3, Timestamp.from(base.plus(index, ChronoUnit.SECONDS)));
                addBatch(courseFavorite, index);
                addBatch(blogFavorite, index);
            }
            courseFavorite.executeBatch();
            blogFavorite.executeBatch();
        }
    }

    private static void seedComments(Connection connection) throws SQLException {
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        try (PreparedStatement comment = connection.prepareStatement("""
                    INSERT INTO comment(comment_id, user_id, content, time, `like`, deleted)
                    VALUES(?, ?, ?, ?, 0, 0)
                    """);
                PreparedStatement courseComment = connection.prepareStatement(
                        "INSERT INTO course_direct_comment(comment_id, course_id) VALUES(?, ?)");
                PreparedStatement blogComment = connection.prepareStatement(
                        "INSERT INTO blog_direct_comment(comment_id, blog_id) VALUES(?, ?)");
                PreparedStatement reply = connection.prepareStatement(
                        "INSERT INTO indirect_comment(comment_id, father_id, layer) VALUES(?, ?, 1)")) {
            for (int index = 0; index < TOP_COMMENT_COUNT + REPLY_COUNT; index++) {
                long commentId = COMMENT_BASE + index;
                comment.setLong(1, commentId);
                comment.setLong(2, USER_BASE + (index % USER_COUNT));
                comment.setString(3, "Deterministic performance comment " + index);
                comment.setTimestamp(4, Timestamp.from(base.plus(index, ChronoUnit.SECONDS)));
                addBatch(comment, index);
                if (index < TOP_COMMENT_COUNT) {
                    if ((index & 1) == 0) {
                        courseComment.setLong(1, commentId);
                        courseComment.setInt(2, COURSE_BASE + (index % COURSE_COUNT));
                        addBatch(courseComment, index);
                    } else {
                        blogComment.setLong(1, commentId);
                        blogComment.setLong(2, BLOG_BASE + verifiedBlogIndex(index % BLOG_COUNT));
                        addBatch(blogComment, index);
                    }
                } else {
                    int replyIndex = index - TOP_COMMENT_COUNT;
                    reply.setLong(1, commentId);
                    reply.setLong(2, COMMENT_BASE + replyIndex);
                    addBatch(reply, replyIndex);
                }
            }
            comment.executeBatch();
            courseComment.executeBatch();
            blogComment.executeBatch();
            reply.executeBatch();
        }
    }

    private static int verifiedBlogIndex(int index) {
        int result = index;
        while (result % 20 >= 17) {
            result = (result + 1) % BLOG_COUNT;
        }
        return result;
    }

    private static void addBatch(PreparedStatement statement, int index) throws SQLException {
        statement.addBatch();
        if ((index + 1) % BATCH_SIZE == 0) {
            statement.executeBatch();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private record Environment(String url, String username, String password) {
        static Environment load() {
            String url = required("CC4C_PERF_DB_URL");
            String username = required("CC4C_PERF_DB_USERNAME");
            String password = required("CC4C_PERF_DB_PASSWORD");
            String confirmation = required("CC4C_PERF_DB_RESET_CONFIRM");
            URI uri = URI.create(url.substring("jdbc:".length()));
            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                throw new IllegalStateException("Performance JDBC URL must contain a database name");
            }
            String database = path.substring(1);
            if (!database.endsWith("_perf_test") || !database.equals(confirmation)) {
                throw new IllegalStateException(
                        "Performance database must end with _perf_test and exactly match confirmation");
            }
            return new Environment(url, username, password);
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required performance environment variable is missing: " + name);
            }
            return value;
        }
    }
}
