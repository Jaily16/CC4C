package com.cc4c.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cc4c.dto.CommentRow;
import com.cc4c.dto.InteractionDtos.CommentResponse;
import com.cc4c.dto.PageResult;
import com.cc4c.dto.UserSnapshot;
import com.cc4c.entity.CommentEntity;
import com.cc4c.mapper.InteractionMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * InteractionCommentAssembler 负责收藏与评论的一项明确运行职责，并保持现有外部行为不变。
 */
final class InteractionCommentAssembler {
    private final InteractionMapper mapper;

    /**
     * 创建 InteractionCommentAssembler 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param mapper 由容器注入的 InteractionMapper 协作组件
     */
    InteractionCommentAssembler(InteractionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param topLevelPage 调用方提供的 {@code topLevelPage} 值
     * @return 包含分页元数据的查询结果
     */
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

    /**
     * 转换当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param comment 调用方提供的 {@code comment} 值
     * @param user 调用方提供的 {@code user} 值
     * @param fatherId 目标对象的稳定标识
     * @param layer 调用方提供的 {@code layer} 值
     * @param fatherName 调用方提供的 {@code fatherName} 值
     * @return 当前操作产生的 CommentResponse 结果
     */
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

    /**
     * MutableComment 负责收藏与评论的一项明确运行职责，并保持现有外部行为不变。
     */
    private static final class MutableComment {
        private final CommentRow row;
        private final List<MutableComment> replies = new ArrayList<>();

        /**
         * 创建 MutableComment 并保存所需协作组件；构造阶段不主动执行外部业务操作。
         *
         * @param row 调用方提供的 {@code row} 值
         */
        private MutableComment(CommentRow row) {
            this.row = row;
        }

        /**
         * 转换当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @param row 调用方提供的 {@code row} 值
         * @return 当前操作产生的 MutableComment 结果
         */
        static MutableComment from(CommentRow row) {
            return new MutableComment(row);
        }

        /**
         * 转换当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @return 当前操作产生的 CommentResponse 结果
         */
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
