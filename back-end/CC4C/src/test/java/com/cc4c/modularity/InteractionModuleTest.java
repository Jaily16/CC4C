package com.cc4c.modularity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(
        module = "interaction",
        mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class InteractionModuleTest extends ModuleTestSupport {
    @Test
    void contextLoads() {
    }
}
