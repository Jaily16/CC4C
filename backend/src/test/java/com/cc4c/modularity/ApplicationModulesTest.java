package com.cc4c.modularity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cc4c.CC4CApplication;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void applicationContainsExactlySixVerifiedModules() {
        ApplicationModules modules = ApplicationModules.of(CC4CApplication.class);
        modules.verify();

        Set<String> names = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("shared", "identity", "catalog", "community", "interaction", "moderation"), names);
    }
}
