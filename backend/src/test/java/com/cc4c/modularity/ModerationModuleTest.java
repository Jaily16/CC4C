package com.cc4c.modularity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(module = "moderation", mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class ModerationModuleTest extends ModuleTestSupport {
    @Test
    void contextLoads() {}
}
