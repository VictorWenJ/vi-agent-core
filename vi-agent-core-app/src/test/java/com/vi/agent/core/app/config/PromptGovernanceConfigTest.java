package com.vi.agent.core.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptGovernanceConfigTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            assertEquals(
                StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT,
                registry.getStructuredLlmOutputContract(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
                    .getStructuredOutputContractKey()
            );
            assertEquals(
                StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT,
                registry.getStructuredLlmOutputContract(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT)
                    .getStructuredOutputContractKey()
            );
            assertSame(registry, rendererRegistry(renderer));
        });
    }

    @Test
    void shouldKeepFormalContractSchemasAlignedWithP2EDesign() throws Exception {
        String stateContract = resourceText("prompt-catalog/system/state_delta_extract/contract.json");
        JsonNode stateRoot = OBJECT_MAPPER.readTree(stateContract);
        assertEquals("object", stateRoot.path("type").asText());
        assertTrue(stateRoot.has("x-structuredOutputContractKey"));
        assertTrue(stateRoot.has("x-outputTarget"));
        assertTrue(stateRoot.has("x-description"));
        assertEquals(Set.of(
            "taskGoalOverride",
            "confirmedFactsAppend",
            "constraintsAppend",
            "userPreferencesPatch",
            "decisionsAppend",
            "openLoopsAppend",
            "openLoopIdsToClose",
            "recentToolOutcomesAppend",
            "workingModeOverride",
            "phaseStatePatch",
            "sourceCandidateIds"
        ), fieldNames(stateRoot.path("properties")));
        for (String forbidden : Set.of(
            "skipped",
            "reason",
            "statePatches",
            "evidenceItems",
            "operation",
            "upsert",
            "remove",
            "patches",
            "operations",
            "debug",
            "locale",
            "timezone"
        )) {
            assertFalse(stateContract.contains("\"" + forbidden + "\""));
        }

        String summaryContract = resourceText("prompt-catalog/system/conversation_summary_extract/contract.json");
        JsonNode summaryRoot = OBJECT_MAPPER.readTree(summaryContract);
        assertEquals("object", summaryRoot.path("type").asText());
        assertTrue(summaryRoot.has("x-structuredOutputContractKey"));
        assertTrue(summaryRoot.has("x-outputTarget"));
        assertTrue(summaryRoot.has("x-description"));
        assertEquals(Set.of("summaryText", "skipped", "reason"), fieldNames(summaryRoot.path("properties")));
        assertFalse(summaryContract.contains("\"sourceMessageIds\""));
    }

    @Test
    void shouldUseDesignVariableNamesInFormalRenderPrompts() {
        String sessionManifest = resourceText("prompt-catalog/system/session_state_render/manifest.yml");
        String sessionPrompt = resourceText("prompt-catalog/system/session_state_render/prompt.md");
        assertTrue(sessionManifest.contains("variableName: stateVersion"));
        assertTrue(sessionManifest.contains("variableName: sessionStateText"));
        assertTrue(sessionPrompt.contains("{{stateVersion}}"));
        assertTrue(sessionPrompt.contains("{{sessionStateText}}"));
        assertFalse(sessionManifest.contains("sessionStateJson"));
        assertFalse(sessionPrompt.contains("sessionStateJson"));

        String summaryManifest = resourceText("prompt-catalog/system/conversation_summary_render/manifest.yml");
        String summaryPrompt = resourceText("prompt-catalog/system/conversation_summary_render/prompt.md");
        assertTrue(summaryManifest.contains("variableName: summaryVersion"));
        assertTrue(summaryManifest.contains("variableName: summaryText"));
        assertTrue(summaryPrompt.contains("{{summaryVersion}}"));
        assertTrue(summaryPrompt.contains("{{summaryText}}"));
        assertFalse(summaryManifest.contains("summaryUpdatedAt"));
        assertFalse(summaryManifest.contains("conversationSummaryText"));
        assertFalse(summaryPrompt.contains("summaryUpdatedAt"));
        assertFalse(summaryPrompt.contains("conversationSummaryText"));
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
    void shouldAllowLocalDevCatalogRevisionForLocalDevAndTestProfiles() {
        for (String profile : Set.of("local", "dev", "test")) {
            contextRunner
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertEquals("local-dev", context.getBean(SystemPromptRegistry.class).catalogRevision()));
        }
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
    void shouldFailWhenNonLocalProfileFallsBackToLocalDevCatalogRevision() {
        contextRunner
            .withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertTrue(context.getStartupFailure() instanceof Exception));
    }

    @Test
    void shouldFailWhenNonLocalProfileUsesLocalDevCatalogRevisionExplicitly() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "vi.agent.system-prompt.catalog-revision=local-dev"
            )
            .run(context -> assertTrue(context.getStartupFailure() instanceof Exception));
    }

    @Test
    void shouldAllowNonLocalProfileWithExplicitCatalogRevision() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "vi.agent.system-prompt.catalog-revision=release-20260428"
            )
            .run(context -> assertEquals(
                "release-20260428",
                context.getBean(SystemPromptRegistry.class).catalogRevision()
            ));
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

    private String resourceText(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, resourcePath);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read test resource: " + resourcePath, ex);
        }
    }

    private Set<String> fieldNames(JsonNode propertiesNode) {
        Set<String> names = new LinkedHashSet<>();
        propertiesNode.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
