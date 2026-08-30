package com.cc4c.modularity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(module = "community", mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class CommunityModuleTest extends ModuleTestSupport {
    @Test
    void contextLoads() {}
}
