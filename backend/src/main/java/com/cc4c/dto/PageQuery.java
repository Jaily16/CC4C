package com.cc4c.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 统一分页请求，页码从 1 起算，每页数量限制为 1 至 100。
 *
 * @param page 从 1 起算的页码
 * @param size 每页记录数量，范围 1 至 100
 */
public record PageQuery(@Min(1) int page, @Min(1) @Max(100) int size) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;

    /**
     * 将从 1 起算的页码换算为 SQL 查询跳过的记录数。
     *
     * @return 使用 long 计算的零起始偏移量
     */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
