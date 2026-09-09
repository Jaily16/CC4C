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

/** 批量加载最多两级回复并组装评论树，分页元数据只统计顶层评论。 */
final class InteractionCommentAssembler {
    private final InteractionMapper mapper;

    /**
     * 接入批量回复查询 Mapper。
     *
     * @param mapper 批量评论与回复查询 Mapper
     */
    InteractionCommentAssembler(InteractionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保留顶层评论顺序，最多执行两轮批量回复查询并挂接父节点；空顶层页不查询回复。
     *
     * @param topLevelPage 只包含顶层评论的分页投影
     * @return 带两级回复的顶层评论分页结果
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
     * 将新评论、作者及父评论信息合并为响应，子评论列表初始化为空。
     *
     * @param comment 新创建的评论实体
     * @param user 评论作者的展示快照
     * @param fatherId 父评论 ID；直接评论时为空
     * @param layer 评论层级，顶层为 0、回复为 1 或 2
     * @param fatherName 父评论作者昵称，可为空
     * @return 新创建评论的展示响应
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

    /** 组装阶段保存评论投影和可追加回复列表的内部节点。 */
    private static final class MutableComment {
        private final CommentRow row;
        private final List<MutableComment> replies = new ArrayList<>();

        /**
         * 保存评论投影，回复列表由字段初始化为空。
         *
         * @param row 当前评论的查询投影
         */
        private MutableComment(CommentRow row) {
            this.row = row;
        }

        /**
         * 由一条评论投影创建尚无子回复的组装节点。
         *
         * @param row 当前评论的查询投影
         * @return 可挂接回复的内部节点
         */
        static MutableComment from(CommentRow row) {
            return new MutableComment(row);
        }

        /**
         * 递归投影已挂接回复，长整数 ID 转为字符串，缺失层级按顶层 0 表示。
         *
         * @return 包含子评论的展示响应
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
