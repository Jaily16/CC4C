package com.cc4c.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * PageQuery 以不可变结构承载共享基础设施数据，并保持现有字段语义。
 *
 * @param page 从零或接口约定起算的页码
 * @param size 受接口上限约束的每页数量
 */
public record PageQuery(@Min(1) int page, @Min(1) @Max(100) int size) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前规则计算或读取的数值
     */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
