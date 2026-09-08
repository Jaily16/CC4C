package com.cc4c.dto;

import java.util.List;

/**
 * 以不可变结构承载共享基础设施计算或查询结果。
 *
 * @param <T> 类型参数
 * @param items 调用方提供的 {@code items} 值
 * @param page 从零或接口约定起算的页码
 * @param size 受接口上限约束的每页数量
 * @param total 调用方提供的 {@code total} 值
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    /**
     * 创建 PageResult 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param items 调用方提供的 {@code items} 值
     * @param page 从零或接口约定起算的页码
     * @param size 受接口上限约束的每页数量
     * @param total 调用方提供的 {@code total} 值
     */
    public PageResult {
        items = List.copyOf(items);
    }

    /**
     * 转换当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 包含分页元数据的查询结果
     */
    public int totalPages() {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }
}
