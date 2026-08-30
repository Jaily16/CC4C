package com.cc4c.modularity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(module = "catalog", mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class CatalogModuleTest extends ModuleTestSupport {
    @Test
    void contextLoads() {}
}
