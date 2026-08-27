package com.cc4c.interaction.internal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.community.api.CommunityLookup;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.shared.PageQuery;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionQueryShapeTest {

    @Test
    void commentTreeUsesOneTopLevelPageAndTwoBulkReplyQueries() {
        InteractionMapper mapper = mock(InteractionMapper.class);
        IdentityLookup identityLookup = mock(IdentityLookup.class);
        CatalogLookup catalogLookup = mock(CatalogLookup.class);
        CommunityLookup communityLookup = mock(CommunityLookup.class);
        InteractionService service = new InteractionService(
                mapper, identityLookup, catalogLookup, communityLookup);

        when(catalogLookup.courseExists(7)).thenReturn(true);
        Page<CommentRow> topPage = new Page<>(1, 10, 1);
        topPage.setRecords(List.of(row(1L, null, 0)));
        when(mapper.selectCourseComments(any(), eq(7))).thenReturn(topPage);
        when(mapper.selectReplies(List.of(1L))).thenReturn(List.of(row(2L, 1L, 1)));
        when(mapper.selectReplies(List.of(2L))).thenReturn(List.of(row(3L, 2L, 2)));

        var result = service.courseComments(7, new PageQuery(1, 10));

        assertEquals(1, result.items().size());
        assertEquals(1, result.items().getFirst().subCommentList().size());
        assertEquals(1, result.items().getFirst().subCommentList().getFirst().subCommentList().size());
        verify(mapper).selectCourseComments(any(), eq(7));
        verify(mapper).selectReplies(List.of(1L));
        verify(mapper).selectReplies(List.of(2L));
    }

    private CommentRow row(long id, Long fatherId, int layer) {
        CommentRow row = new CommentRow();
        row.setCommentId(id);
        row.setUserId(10L + id);
        row.setContent("comment-" + id);
        row.setTime(new Date());
        row.setLike(0);
        row.setFatherId(fatherId);
        row.setLayer(layer);
        row.setUserName("user-" + id);
        return row;
    }
}
