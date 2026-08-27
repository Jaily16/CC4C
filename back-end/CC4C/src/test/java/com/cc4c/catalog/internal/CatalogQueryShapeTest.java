package com.cc4c.catalog.internal;

import com.cc4c.shared.BusinessCache;
import com.cc4c.shared.BusinessCacheProperties;
import com.cc4c.shared.BusinessCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogQueryShapeTest {

    @Test
    void modulesUseOneModuleQueryAndOneBulkCourseQuery() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogService service = new CatalogService(mapper, disabledCache());
        when(mapper.selectModules(7)).thenReturn(List.of(module(7, 1), module(7, 2)));
        when(mapper.selectCourseNamesByLanguage(7)).thenReturn(List.of(
                courseName(1, "first"), courseName(1, "second"), courseName(2, "third")));

        var result = service.modules(7);

        assertEquals(List.of("first", "second"), result.getFirst().courseList());
        assertEquals(List.of("third"), result.get(1).courseList());
        verify(mapper).selectModules(7);
        verify(mapper).selectCourseNamesByLanguage(7);
    }

    @Test
    void recommendationUsesOneModuleQueryAndOneBulkCourseQuery() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogService service = new CatalogService(mapper, disabledCache());
        when(mapper.selectModulesForRecommendation(7, 0, 1))
                .thenReturn(List.of(module(7, 3), module(7, 4)));
        when(mapper.selectRecommendedCourseNamesByLanguage(7, 1, 2)).thenReturn(List.of(
                courseName(3, "recommended-a"), courseName(4, "recommended-b")));

        var result = service.recommend(7, 1);

        assertEquals(List.of("recommended-a"), result.getFirst().courseList());
        assertEquals(List.of("recommended-b"), result.get(1).courseList());
        verify(mapper).selectModulesForRecommendation(7, 0, 1);
        verify(mapper).selectRecommendedCourseNamesByLanguage(7, 1, 2);
    }

    private BusinessCache disabledCache() {
        @SuppressWarnings("unchecked")
        ObjectProvider<BusinessCacheStore> provider = mock(ObjectProvider.class);
        return new BusinessCache(
                new ObjectMapper().findAndRegisterModules(),
                new BusinessCacheProperties(false, "", "", false),
                provider);
    }

    private CourseModuleRow module(int languageId, int priority) {
        CourseModuleRow row = new CourseModuleRow();
        row.setLanguageId(languageId);
        row.setPriority(priority);
        row.setModuleName("module-" + priority);
        row.setLevel(0);
        return row;
    }

    private ModuleCourseNameRow courseName(int priority, String name) {
        ModuleCourseNameRow row = new ModuleCourseNameRow();
        row.setPriority(priority);
        row.setCourseName(name);
        return row;
    }
}
