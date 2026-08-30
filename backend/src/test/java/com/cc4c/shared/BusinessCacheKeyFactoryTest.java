package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BusinessCacheKeyFactoryTest {

    private final BusinessCacheKeyFactory factory = new BusinessCacheKeyFactory("cc4c:test:cache");

    @Test
    void preservesVersionedNamespaceAndSha256LogicalKey() {
        String dataKey = factory.dataKey("catalog:detail", "7", "course-a");

        assertEquals(
                "cc4c:test:cache:v1:catalog:detail:g7:458695add57b3b61cf7e2548c298d9ae7c23bfdf5e9ac1bf06885bcf3a650d06",
                dataKey);
        assertEquals("cc4c:test:cache:v1:catalog:detail:generation", factory.generationKey("catalog:detail"));
        assertEquals(dataKey + ":lock", factory.lockKey(dataKey));
    }

    @Test
    void rejectsInvalidRegionsBeforeGeneratingKeys() {
        assertThrows(IllegalArgumentException.class, () -> factory.generationKey("Catalog"));
        assertThrows(IllegalArgumentException.class, () -> factory.dataKey("a", "0", "logical"));
    }
}
