package com.cc4c.dto;

import java.util.List;

/**
 * 分页接口响应，携带结果列表、总数和前后页标志。
 *
 * @param <T> 数据元素类型
 * @param items 当前页的数据列表
 * @param page 从 1 起算的页码
 * @param size 每页记录数量
 * @param total 匹配条件的记录总数
 * @param totalPages 按总数和页大小计算的页数
 * @param hasNext 页码是否小于总页数
 * @param hasPrevious 页码是否大于 1
 */
public record PageResponse<T>(
        List<T> items, int page, int size, long total, int totalPages, boolean hasNext, boolean hasPrevious) {
    /**
     * 将服务层分页结果转换为响应，并按页码与总页数计算前后页标志。
     *
     * @param <T> 数据元素类型
     * @param result 服务层返回的分页结果
     * @return 带前后页标志的分页响应
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
