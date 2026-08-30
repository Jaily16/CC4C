package com.cc4c.catalog.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cc4c.catalog.CatalogDtos.CourseModuleCreateRequest;
import com.cc4c.shared.BusinessCache;
import org.junit.jupiter.api.Test;

class CatalogCacheInvalidationTest {

    @Test
    void moduleCreationInvalidatesOnlyStructureRegions() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        BusinessCache cache = mock(BusinessCache.class);
        CatalogService service = new CatalogService(mapper, cache);
        when(mapper.findLanguageName(1)).thenReturn("Java");
        when(mapper.moduleExists(1, 9)).thenReturn(false);

        service.createModule(new CourseModuleCreateRequest(1, 9, "advanced", 1));

        verify(mapper).insertModule(1, 9, "advanced", 1);
        verify(cache).invalidateAfterCommit("catalog:modules", "catalog:recommend");
    }
}
