package com.cc4c.service;

/** 向其他业务提供语言、课程存在性查询及课程热度缓存失效入口。 */
public interface CatalogLookup {
    /**
     * 检查未删除的语言是否存在。
     *
     * @param languageId 语言 ID
     * @return 语言存在时为 true
     */
    boolean languageExists(int languageId);

    /**
     * 检查未删除的课程是否存在。
     *
     * @param courseId 课程 ID
     * @return 课程存在时为 true
     */
    boolean courseExists(int courseId);

    /** 在当前事务提交后使课程首页热度缓存失效。 */
    void invalidateCoursePopularity();
}
