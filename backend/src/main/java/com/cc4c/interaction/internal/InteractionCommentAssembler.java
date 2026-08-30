package com.cc4c.interaction.internal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cc4c.identity.api.UserSnapshot;
import com.cc4c.interaction.InteractionDtos.CommentResponse;
import com.cc4c.shared.PageResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 组装两级评论树，并集中维护评论创建响应的字段映射。 */
final class InteractionCommentAssembler {
    private final InteractionMapper mapper;

    InteractionCommentAssembler(InteractionMapper mapper) {
        this.mapper = mapper;
    }

    PageResult<CommentResponse> assemble(IPage<CommentRow> topLevelPage) {
        Map<Long, MutableComment> topLevel = new LinkedHashMap<>();
        topLevelPage.getRecords().forEach(row -> topLevel.put(row.getCommentId(), MutableComment.from(row)));

        if (!topLevel.isEmpty()) {
            List<CommentRow> firstRows = mapper.selectReplies(List.copyOf(topLevel.keySet()));
            Map<Long, MutableComment> firstLevel = new LinkedHashMap<>();
            firstRows.forEach(row -> {
                MutableComment reply = MutableComment.from(row);
                firstLevel.put(row.getCommentId(), reply);
                MutableComment parent = topLevel.get(row.getFatherId());
                if (parent != null) {
                    parent.replies.add(reply);
                }
            });
            if (!firstLevel.isEmpty()) {
                mapper.selectReplies(List.copyOf(firstLevel.keySet())).forEach(row -> {
                    MutableComment parent = firstLevel.get(row.getFatherId());
                    if (parent != null) {
                        parent.replies.add(MutableComment.from(row));
                    }
                });
            }
        }

        return new PageResult<>(
                topLevel.values().stream().map(MutableComment::toResponse).toList(),
                Math.toIntExact(topLevelPage.getCurrent()),
                Math.toIntExact(topLevelPage.getSize()),
                topLevelPage.getTotal());
    }

    CommentResponse toCreatedResponse(
            CommentEntity comment, UserSnapshot user, Long fatherId, int layer, String fatherName) {
        return new CommentResponse(
                Long.toString(comment.getCommentId()),
                Long.toString(comment.getUserId()),
                comment.getContent(),
                comment.getTime(),
                comment.getLike(),
                fatherId == null ? null : Long.toString(fatherId),
                layer,
                user.name(),
                user.avatar(),
                fatherName,
                List.of());
    }

    private static final class MutableComment {
        private final CommentRow row;
        private final List<MutableComment> replies = new ArrayList<>();

        private MutableComment(CommentRow row) {
            this.row = row;
        }

        static MutableComment from(CommentRow row) {
            return new MutableComment(row);
        }

        CommentResponse toResponse() {
            return new CommentResponse(
                    Long.toString(row.getCommentId()),
                    Long.toString(row.getUserId()),
                    row.getContent(),
                    row.getTime(),
                    row.getLike(),
                    row.getFatherId() == null ? null : Long.toString(row.getFatherId()),
                    row.getLayer() == null ? 0 : row.getLayer(),
                    row.getUserName(),
                    row.getUserAvatar(),
                    row.getFatherName(),
                    replies.stream().map(MutableComment::toResponse).toList());
        }
    }
}
