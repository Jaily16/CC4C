package com.cc4c.shared;

import java.util.List;

/** PageResult 是不可变的数据载体，保持现有字段语义和序列化契约。 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }
}
