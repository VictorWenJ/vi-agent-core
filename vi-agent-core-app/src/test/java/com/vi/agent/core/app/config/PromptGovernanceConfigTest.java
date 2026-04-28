package com.vi.agent.core.app.config;

import com.vi.agent.core.app.config.properties.SystemPromptProperties;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import com.vi.agent.core.runtime.prompt.PromptRenderRequest;
import com.vi.agent.core.runtime.prompt.PromptRenderer;
import com.vi.agent.core.runtime.prompt.SystemPromptRegistry;
import com.vi.agent.core.runtime.prompt.TextPromptRenderResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptGovernanceConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class
            )
        )
        .withUserConfiguration(SystemPromptProperties.class, PromptGovernanceConfig.class);

    @Test
    void shouldLoadFormalAppCatalogAndExposeRegistryAndRenderer() {
        contextRunner.run(context -> {
            SystemPromptRegistry registry = context.getBean(SystemPromptRegistry.class);
            PromptRenderer renderer = context.getBean(PromptRenderer.class);

            for (SystemPromptKey promptKey : SystemPromptKey.values()) {
                assertEquals(promptKey, registry.get(promptKey).getPromptKey());
            }
            assertTrue(registry.getStructuredLlmOutputContract(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT).isPresent());
            assertTrue(
                registry.getStructuredLlmOutputContract(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT)
                    .isPresent()
            );
            assertSame(registry, rendererRegistry(renderer));
        });
    }

    @Test
    void shouldFailStartupWhenFailFastCatalogResourceIsMissing() {
        contextRunner
            .withPropertyValues(
                "vi.agent.system-prompt.fail-fast=true",
                "vi.agent.system-prompt.catalog-base-path=prompt-catalog/missing"
            )
            .run(context -> assertTrue(context.getStartupFailure() instanceof Exception));
    }

    @Test
    void shouldUseLocalDevCatalogRevisionByDefault() {
        contextRunner.run(context -> {
            SystemPromptRegistry registry = context.getBean(SystemPromptRegistry.class);

            assertEquals("local-dev", registry.catalogRevision());
        });
    }

    @Test
    void shouldFailWhenNonLocalProfileUsesBlankCatalogRevision() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "vi.agent.system-prompt.catalog-revision="
            )
            .run(context -> assertTrue(context.getStartupFailure() instanceof Exception));
    }

    @Test
    void shouldNotUseContentHashAsCatalogRevision() {
        contextRunner.run(context -> {
            SystemPromptRegistry registry = context.getBean(SystemPromptRegistry.class);

            assertFalse(registry.templateContentHash(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
                .equals(registry.catalogRevision()));
            assertEquals("local-dev", registry.catalogRevision());
        });
    }

    @Test
    void shouldRenderFromStartupRegistrySnapshotWithoutRepositoryAccessInRenderer() {
        contextRunner.run(context -> {
            PromptRenderer renderer = context.getBean(PromptRenderer.class);

            TextPromptRenderResult result = (TextPromptRenderResult) renderer.render(PromptRenderRequest.builder()
                .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
                .variable("agentMode", "chat")
                .variable("workingMode", "general")
                .variable("phaseStateText", "ready")
                .build());

            assertTrue(result.getRenderedText().contains("Agent mode: chat"));
            assertFalse(Arrays.stream(PromptRenderer.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getName().contains("Repository")));
        });
    }

    private SystemPromptRegistry rendererRegistry(PromptRenderer renderer) throws Exception {
        Field field = PromptRenderer.class.getDeclaredField("systemPromptRegistry");
        field.setAccessible(true);
        return (SystemPromptRegistry) field.get(renderer);
    }
}
