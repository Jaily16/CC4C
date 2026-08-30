package com.cc4c.shared;

import java.util.List;

/** PageResponse 是不可变的数据载体，保持现有字段语义和序列化契约。 */
public record PageResponse<T>(
        List<T> items, int page, int size, long total, int totalPages, boolean hasNext, boolean hasPrevious) {
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
