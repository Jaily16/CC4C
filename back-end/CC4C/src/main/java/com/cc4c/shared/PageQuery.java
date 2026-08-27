package com.cc4c.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQuery(
        @Min(1) int page,
        @Min(1) @Max(100) int size
) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;

    public long offset() {
        return (long) (page - 1) * size;
    }
}
