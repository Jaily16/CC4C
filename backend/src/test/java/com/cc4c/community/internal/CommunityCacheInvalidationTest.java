package com.cc4c.community.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.community.CommunityDtos.BlogSubmitRequest;
import com.cc4c.community.api.BlogSubmittedNotificationV1;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.identity.api.IdentityNotificationLookup;
import com.cc4c.shared.BusinessCache;
import com.cc4c.shared.MessagingProperties;
import com.cc4c.shared.RedisRateLimiter;
import com.cc4c.shared.TransactionalOutbox;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommunityCacheInvalidationTest {

    @Test
    void approvalInvalidatesAllPublicBlogRegions() {
        Fixture fixture = fixture();
        BlogEntity pending = blog(42L, 0);
        when(fixture.mapper.selectById(42L)).thenReturn(pending);

        fixture.service.approve(42L);

        verify(fixture.mapper).updateById(pending);
        verify(fixture.cache)
                .invalidateAfterCommit("community:home", "community:all", "community:language", "community:detail");
    }

    @Test
    void denialInvalidatesAllPublicBlogRegions() {
        Fixture fixture = fixture();
        BlogEntity pending = blog(43L, 0);
        when(fixture.mapper.selectById(43L)).thenReturn(pending);

        fixture.service.deny(43L);

        verify(fixture.mapper).updateById(pending);
        verify(fixture.cache)
                .invalidateAfterCommit("community:home", "community:all", "community:language", "community:detail");
    }

    @Test
    void missingAuthorContactCreatesControlledFailureWithoutBlockingModeration() {
        Fixture fixture = fixture();
        BlogEntity pending = blog(45L, 0);
        when(fixture.mapper.selectById(45L)).thenReturn(pending);

        fixture.service.approve(45L);

        verify(fixture.outbox)
                .appendPermanentFailure(
                        eq("community.blog.reviewed.v1"),
                        eq("blog"),
                        eq("45"),
                        any(),
                        any(),
                        eq(null),
                        eq("RECIPIENT_UNAVAILABLE"));
    }

    @Test
    void authorDeletionInvalidatesAllPublicBlogRegions() {
        Fixture fixture = fixture();
        BlogEntity published = blog(44L, 1);
        when(fixture.currentActor.requiredUserId()).thenReturn(7L);
        when(fixture.mapper.selectById(44L)).thenReturn(published);

        fixture.service.delete(44L);

        verify(fixture.mapper).deleteById(44L);
        verify(fixture.cache)
                .invalidateAfterCommit("community:home", "community:all", "community:language", "community:detail");
    }

    @Test
    void clickStaysSynchronousInMysqlWithoutCacheInvalidation() {
        Fixture fixture = fixture();
        when(fixture.mapper.incrementClick(42L)).thenReturn(1);

        fixture.service.click(42L);

        verify(fixture.mapper).incrementClick(42L);
        verify(fixture.cache, never())
                .invalidateAfterCommit("community:home", "community:all", "community:language", "community:detail");
    }

    @Test
    void submissionCreatesOneEncryptedOutboxEventPerConfiguredReviewer() {
        Fixture fixture = fixture();
        when(fixture.currentActor.requiredUserId()).thenReturn(7L);
        when(fixture.catalogLookup.languageExists(1)).thenReturn(true);
        when(fixture.messagingProperties.moderationRecipientList())
                .thenReturn(List.of("first@example.com", "second@example.com"));
        doAnswer(invocation -> {
                    BlogEntity inserted = invocation.getArgument(0);
                    inserted.setBlogId(46L);
                    return 1;
                })
                .when(fixture.mapper)
                .insert(any(BlogEntity.class));

        fixture.service.submit(new BlogSubmitRequest("blog", "content", List.of(1)));

        org.mockito.ArgumentCaptor<BlogSubmittedNotificationV1> payloads =
                org.mockito.ArgumentCaptor.forClass(BlogSubmittedNotificationV1.class);
        verify(fixture.outbox, times(2))
                .append(eq("community.blog.submitted.v1"), eq("blog"), eq("46"), payloads.capture(), any(), eq(null));
        assertEquals(
                Set.of("first@example.com", "second@example.com"),
                payloads.getAllValues().stream()
                        .map(BlogSubmittedNotificationV1::recipientEmail)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private Fixture fixture() {
        BlogMapper mapper = mock(BlogMapper.class);
        IdentityLookup identityLookup = mock(IdentityLookup.class);
        IdentityNotificationLookup identityNotificationLookup = mock(IdentityNotificationLookup.class);
        CatalogLookup catalogLookup = mock(CatalogLookup.class);
        CurrentActor currentActor = mock(CurrentActor.class);
        RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
        BusinessCache cache = mock(BusinessCache.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        MessagingProperties messagingProperties = mock(MessagingProperties.class);
        when(identityNotificationLookup.findNotificationContact(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
        return new Fixture(
                mapper,
                cache,
                currentActor,
                outbox,
                catalogLookup,
                messagingProperties,
                new CommunityService(
                        mapper,
                        identityLookup,
                        identityNotificationLookup,
                        catalogLookup,
                        currentActor,
                        rateLimiter,
                        cache,
                        outbox,
                        messagingProperties));
    }

    private BlogEntity blog(long id, int state) {
        BlogEntity blog = new BlogEntity();
        blog.setBlogId(id);
        blog.setWriterId(7L);
        blog.setTitle("blog");
        blog.setContent("content");
        blog.setPublishTime(new Date());
        blog.setClick(0);
        blog.setState(state);
        return blog;
    }

    private record Fixture(
            BlogMapper mapper,
            BusinessCache cache,
            CurrentActor currentActor,
            TransactionalOutbox outbox,
            CatalogLookup catalogLookup,
            MessagingProperties messagingProperties,
            CommunityService service) {}
}
