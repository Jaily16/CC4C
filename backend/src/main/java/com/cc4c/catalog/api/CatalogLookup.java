package com.cc4c.catalog.api;

/** CatalogLookup 定义模块之间稳定、可验证的公开契约。 */
public interface CatalogLookup {
    boolean languageExists(int languageId);

    boolean courseExists(int courseId);

    void invalidateCoursePopularity();
}
