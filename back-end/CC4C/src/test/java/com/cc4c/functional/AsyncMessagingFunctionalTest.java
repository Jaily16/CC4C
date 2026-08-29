package com.cc4c.functional;

import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.TransactionalOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AsyncMessagingFunctionalTest extends FunctionalTestSupport {
    private static final String CODE = "246810";

    @Autowired
    private TransactionalOutbox outbox;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void verificationRequestIsAcceptedBeforeMailAndPersistsOnlyEncryptedPayload() throws Exception {
        String recipient = unique("async_") + "@example.com";
        when(verificationCodeGenerator.generate()).thenReturn(CODE);

        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + recipient + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.verificationCode").doesNotExist());

        verifyNoInteractions(javaMailSender);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, payload_ciphertext
                FROM async_outbox
                WHERE event_type = ?
                ORDER BY id DESC LIMIT 1
                """, AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED);
        assertEquals("PENDING", row.get("status"));
        String encryptedBytes = new String((byte[]) row.get("payload_ciphertext"), StandardCharsets.UTF_8);
        assertFalse(encryptedBytes.contains(recipient));
        assertFalse(encryptedBytes.contains(CODE));
    }

    @Test
    void blogSubmissionAndReviewWriteVersionedEventsInTheirBusinessTransactions() throws Exception {
        UserFixture writer = createUser();
        AdminFixture administrator = createAdmin();
        LanguageFixture language = createLanguage();
        String title = unique("async_blog_");
        long submittedBefore = countOutboxEvents(AsyncEventTypes.BLOG_SUBMITTED);
        long reviewedBefore = countOutboxEvents(AsyncEventTypes.BLOG_REVIEWED);

        mockMvc.perform(post("/blogs/submit").with(asUser(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "content", "content that must not enter the notification",
                                "languageList", List.of(language.id())))))
                .andExpect(status().isCreated());

        Long blogId = jdbcTemplate.queryForObject(
                "SELECT blog_id FROM blog WHERE title = ?", Long.class, title);
        assertEquals(submittedBefore + 1, countOutboxEvents(AsyncEventTypes.BLOG_SUBMITTED));
        byte[] encryptedPayload = jdbcTemplate.queryForObject("""
                SELECT payload_ciphertext FROM async_outbox
                WHERE event_type = ? ORDER BY id DESC LIMIT 1
                """, byte[].class, AsyncEventTypes.BLOG_SUBMITTED);
        assertFalse(new String(encryptedPayload, StandardCharsets.UTF_8)
                .contains("content that must not enter"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/blogs/approve/{id}", blogId)
                        .with(asAdministrator(administrator)).with(csrf()))
                .andExpect(status().isOk());
        assertEquals(reviewedBefore + 1, countOutboxEvents(AsyncEventTypes.BLOG_REVIEWED));
    }

    @Test
    void outboxWriteRollsBackWithItsOwningTransaction() {
        long before = countOutboxEvents(AsyncEventTypes.BLOG_SUBMITTED);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(ignored -> {
            outbox.append(
                    AsyncEventTypes.BLOG_SUBMITTED,
                    "blog",
                    "rollback-test",
                    Map.of("safe", true),
                    Instant.now(),
                    null);
            throw new IllegalStateException("force rollback");
        }));

        assertEquals(before, countOutboxEvents(AsyncEventTypes.BLOG_SUBMITTED));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outboxRejectsCallsWithoutAnActiveTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () ->
                outbox.append(
                        AsyncEventTypes.BLOG_SUBMITTED,
                        "blog",
                        "no-transaction",
                        Map.of("safe", true),
                        Instant.now(),
                        null));
    }

    @Test
    void adminCanInspectRetryAndIgnoreWithoutSeeingPayload() throws Exception {
        String recipient = unique("recover_") + "@example.com";
        when(verificationCodeGenerator.generate()).thenReturn(CODE);
        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + recipient + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isAccepted());
        String eventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM async_outbox ORDER BY id DESC LIMIT 1", String.class);
        jdbcTemplate.update(
                "UPDATE async_outbox SET status = 'DEAD', error_code = 'MAIL_TRANSIENT' WHERE event_id = ?",
                eventId);

        UserFixture user = createUser();
        AdminFixture administrator = createAdmin();
        mockMvc.perform(get("/admin/messaging/messages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/messaging/messages").with(asUser(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/messaging/messages")
                        .with(asAdministrator(administrator))
                        .param("status", "DEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventId").value(eventId))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("MAIL_TRANSIENT"))
                .andExpect(jsonPath("$.data.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].recipientEmail").doesNotExist());

        mockMvc.perform(post("/admin/messaging/messages/{eventId}/retry", eventId)
                        .with(asAdministrator(administrator)).with(csrf()))
                .andExpect(status().isAccepted());
        Map<String, Object> retried = jdbcTemplate.queryForMap(
                "SELECT generation, status FROM async_outbox WHERE event_id = ?", eventId);
        assertEquals(1, ((Number) retried.get("generation")).intValue());
        assertEquals("PENDING", retried.get("status"));
        mockMvc.perform(post("/admin/messaging/messages/{eventId}/retry", eventId)
                        .with(asAdministrator(administrator)).with(csrf()))
                .andExpect(status().isConflict());

        jdbcTemplate.update(
                "UPDATE async_outbox SET status = 'DEAD', error_code = 'MAIL_TRANSIENT' WHERE event_id = ?",
                eventId);
        mockMvc.perform(post("/admin/messaging/messages/{eventId}/ignore", eventId)
                        .with(asAdministrator(administrator)).with(csrf()))
                .andExpect(status().isOk());
        assertEquals("IGNORED", jdbcTemplate.queryForObject(
                "SELECT status FROM async_outbox WHERE event_id = ?", String.class, eventId));
        assertEquals(administrator.id(), jdbcTemplate.queryForObject(
                "SELECT ignored_by FROM async_outbox WHERE event_id = ?", String.class, eventId));
    }

    @Test
    void expiredVerificationMessageCannotBeRetried() throws Exception {
        String recipient = unique("expired_") + "@example.com";
        when(verificationCodeGenerator.generate()).thenReturn(CODE);
        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + recipient + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isAccepted());
        String eventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM async_outbox ORDER BY id DESC LIMIT 1", String.class);
        jdbcTemplate.update("""
                UPDATE async_outbox
                SET status = 'DEAD', expires_at = ?
                WHERE event_id = ?
                """, Timestamp.from(Instant.now().minusSeconds(1)), eventId);

        AdminFixture administrator = createAdmin();
        mockMvc.perform(post("/admin/messaging/messages/{eventId}/retry", eventId)
                        .with(asAdministrator(administrator)).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingAuthorContactRecordsControlledDeadMessageWithoutRollingBackApproval() throws Exception {
        UserFixture writer = createUser();
        BlogFixture pending = createBlog(writer, 0);
        AdminFixture administrator = createAdmin();
        jdbcTemplate.update("UPDATE user SET deleted = 1 WHERE user_id = ?", writer.id());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/blogs/approve/{id}", pending.id())
                        .with(asAdministrator(administrator)).with(csrf()))
                .andExpect(status().isOk());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT state FROM blog WHERE blog_id = ?", Integer.class, pending.id()));
        Map<String, Object> failure = jdbcTemplate.queryForMap("""
                SELECT status, error_code FROM async_outbox
                WHERE event_type = ? AND aggregate_id = ?
                ORDER BY id DESC LIMIT 1
                """, AsyncEventTypes.BLOG_REVIEWED, Long.toString(pending.id()));
        assertEquals("DEAD", failure.get("status"));
        assertEquals("RECIPIENT_UNAVAILABLE", failure.get("error_code"));
    }

    private long countOutboxEvents(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM async_outbox WHERE event_type = ?",
                Long.class,
                eventType);
    }
}
