package com.cc4c.shared;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
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
