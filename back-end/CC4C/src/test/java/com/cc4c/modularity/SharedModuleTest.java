package com.cc4c.modularity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(module = "shared")
class SharedModuleTest extends ModuleTestSupport {
    @Test
    void contextLoads() {
    }
}
