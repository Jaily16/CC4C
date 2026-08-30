package com.cc4c.modularity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(module = "identity", mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class IdentityModuleTest extends ModuleTestSupport {
    @Test
    void contextLoads() {}
}
