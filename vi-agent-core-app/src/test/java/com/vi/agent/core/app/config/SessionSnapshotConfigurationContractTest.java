package com.vi.agent.core.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSnapshotConfigurationContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(ConfigurationProbe.class);

    @Test
    void newSessionSnapshotPropertiesShouldBind() {
        contextRunner
            .withPropertyValues(
                "vi.agent.runtime.session-working-set.max-completed-turns=7",
                "vi.agent.redis.ttl.session-working-set-seconds=101",
                "vi.agent.redis.ttl.session-state-seconds=102",
                "vi.agent.redis.ttl.session-summary-seconds=103"
            )
            .run(context -> {
                ConfigurationProbe probe = context.getBean(ConfigurationProbe.class);

                assertEquals(7, probe.maxCompletedTurns);
                assertEquals(101L, probe.sessionWorkingSetTtlSeconds);
                assertEquals(102L, probe.sessionStateTtlSeconds);
                assertEquals(103L, probe.sessionSummaryTtlSeconds);
            });
    }

    @Test
    void oldSessionContextPropertiesShouldNotBindAsFormalEntry() {
        contextRunner
            .withPropertyValues(
                "vi.agent.runtime.session-context.max-turns=7",
                "vi.agent.redis.ttl.session-context-seconds=101"
            )
            .run(context -> {
                ConfigurationProbe probe = context.getBean(ConfigurationProbe.class);

                assertEquals(5, probe.maxCompletedTurns);
                assertEquals(1800L, probe.sessionWorkingSetTtlSeconds);
                assertEquals(1800L, probe.sessionStateTtlSeconds);
                assertEquals(1800L, probe.sessionSummaryTtlSeconds);
            });
    }

    @Test
    void profileConfigurationFilesShouldNotContainOldSessionContextKeys() throws IOException {
        List<String> resources = List.of(
            "application.yml",
            "application-dev.yml",
            "application-docker.yml",
            "application-test.yml"
        );

        for (String resource : resources) {
            String content = readResource(resource);

            assertFalse(content.contains("session-context"));
            assertFalse(content.contains("sessionContext"));
            assertFalse(content.contains("SessionContext"));
            assertFalse(content.contains("session-context-seconds"));
            assertFalse(content.contains("SESSION_CONTEXT"));
        }

        String application = readResource("application.yml");
        assertTrue(application.contains("session-working-set"));
        assertTrue(application.contains("max-completed-turns"));
        assertTrue(application.contains("session-working-set-seconds"));
        assertTrue(application.contains("session-state-seconds"));
        assertTrue(application.contains("session-summary-seconds"));
    }

    private String readResource(String resourcePath) throws IOException {
        return StreamUtils.copyToString(
            new ClassPathResource(resourcePath).getInputStream(),
            StandardCharsets.UTF_8
        );
    }

    static class ConfigurationProbe {

        @Value("${vi.agent.runtime.session-working-set.max-completed-turns:5}")
        int maxCompletedTurns;

        @Value("${vi.agent.redis.ttl.session-working-set-seconds:1800}")
        long sessionWorkingSetTtlSeconds;

        @Value("${vi.agent.redis.ttl.session-state-seconds:1800}")
        long sessionStateTtlSeconds;

        @Value("${vi.agent.redis.ttl.session-summary-seconds:1800}")
        long sessionSummaryTtlSeconds;
    }
}
