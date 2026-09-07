package com.cc4c.catalog.api;

/**
 * 定义课程目录跨模块调用所需的稳定能力边界。
 */
public interface CatalogLookup {
    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param languageId 目标对象的稳定标识
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    boolean languageExists(int languageId);

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param courseId 目标对象的稳定标识
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    boolean courseExists(int courseId);

    /**
     * 删除或失效当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     */
    void invalidateCoursePopularity();
}
