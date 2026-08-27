package com.cc4c.community.internal;

import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.shared.BusinessCache;
import com.cc4c.shared.RedisRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityCacheInvalidationTest {

    @Test
    void approvalInvalidatesAllPublicBlogRegions() {
        Fixture fixture = fixture();
        BlogEntity pending = blog(42L, 0);
        when(fixture.mapper.selectById(42L)).thenReturn(pending);

        fixture.service.approve(42L);

        verify(fixture.mapper).updateById(pending);
        verify(fixture.cache).invalidateAfterCommit(
                "community:home", "community:all", "community:language", "community:detail");
    }

    @Test
    void denialInvalidatesAllPublicBlogRegions() {
        Fixture fixture = fixture();
        BlogEntity pending = blog(43L, 0);
        when(fixture.mapper.selectById(43L)).thenReturn(pending);

        fixture.service.deny(43L);

        verify(fixture.mapper).updateById(pending);
        verify(fixture.cache).invalidateAfterCommit(
                "community:home", "community:all", "community:language", "community:detail");
    }

    @Test
    void authorDeletionInvalidatesAllPublicBlogRegions() {
        Fixture fixture = fixture();
        BlogEntity published = blog(44L, 1);
        when(fixture.currentActor.requiredUserId()).thenReturn(7L);
        when(fixture.mapper.selectById(44L)).thenReturn(published);

        fixture.service.delete(44L);

        verify(fixture.mapper).deleteById(44L);
        verify(fixture.cache).invalidateAfterCommit(
                "community:home", "community:all", "community:language", "community:detail");
    }

    @Test
    void clickStaysSynchronousInMysqlWithoutCacheInvalidation() {
        Fixture fixture = fixture();
        when(fixture.mapper.incrementClick(42L)).thenReturn(1);

        fixture.service.click(42L);

        verify(fixture.mapper).incrementClick(42L);
        verify(fixture.cache, never()).invalidateAfterCommit(
                "community:home", "community:all", "community:language", "community:detail");
    }

    private Fixture fixture() {
        BlogMapper mapper = mock(BlogMapper.class);
        IdentityLookup identityLookup = mock(IdentityLookup.class);
        CatalogLookup catalogLookup = mock(CatalogLookup.class);
        CurrentActor currentActor = mock(CurrentActor.class);
        RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
        BusinessCache cache = mock(BusinessCache.class);
        return new Fixture(
                mapper,
                cache,
                currentActor,
                new CommunityService(
                        mapper, identityLookup, catalogLookup, currentActor, rateLimiter, cache));
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
            CommunityService service) {
    }
}
