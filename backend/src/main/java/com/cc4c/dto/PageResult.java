package com.cc4c.dto;

import java.util.List;

/**
 * 服务层分页结果；构造时复制列表，阻止调用方增删其元素。
 *
 * @param <T> 数据元素类型
 * @param items 当前页的数据列表
 * @param page 从 1 起算的页码
 * @param size 每页记录数量
 * @param total 匹配条件的记录总数
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    /**
     * 保存分页元数据并通过 List.copyOf 固定结果列表；列表及其元素不得为空。
     *
     * @param items 当前页的数据列表
     * @param page 从 1 起算的页码
     * @param size 每页记录数量
     * @param total 匹配条件的记录总数
     */
    public PageResult {
        items = List.copyOf(items);
    }

    /**
     * 将记录总数除以页大小并向上取整；没有记录时返回零页。
     *
     * @return 总页数
     */
    public int totalPages() {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }
}
