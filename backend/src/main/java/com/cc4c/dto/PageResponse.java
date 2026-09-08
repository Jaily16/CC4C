package com.cc4c.dto;

import java.util.List;

/**
 * 承载共享基础设施接口的脱敏响应字段，不暴露内部凭据或异常。
 *
 * @param <T> 类型参数
 * @param items 调用方提供的 {@code items} 值
 * @param page 从零或接口约定起算的页码
 * @param size 受接口上限约束的每页数量
 * @param total 调用方提供的 {@code total} 值
 * @param totalPages 调用方提供的 {@code totalPages} 值
 * @param hasNext 调用方提供的 {@code hasNext} 值
 * @param hasPrevious 调用方提供的 {@code hasPrevious} 值
 */
public record PageResponse<T>(
        List<T> items, int page, int size, long total, int totalPages, boolean hasNext, boolean hasPrevious) {
    /**
     * 转换当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param <T> 方法使用的类型参数
     * @param result 调用方提供的 {@code result} 值
     * @return 包含分页元数据的查询结果
     */
    public static <T> PageResponse<T> from(PageResult<T> result) {
        int totalPages = result.totalPages();
        return new PageResponse<>(
                result.items(),
                result.page(),
                result.size(),
                result.total(),
                totalPages,
                result.page() < totalPages,
                result.page() > 1);
    }
}
