package com.vi.agent.core.infra.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryExtractPromptTemplate;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.StateDeltaExtractPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSystemPromptCatalogRepositoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldLoadAllSystemPromptsAndContractsFromClasspath() {
        ResourceSystemPromptCatalogRepository repository =
            new ResourceSystemPromptCatalogRepository("prompt-catalog/system", "test-revision");

        for (SystemPromptKey promptKey : SystemPromptKey.values()) {
            AbstractPromptTemplate template = repository.findTemplate(promptKey).orElseThrow();
            assertEquals(promptKey, template.getPromptKey());
            assertEquals("test-revision", repository.catalogRevision());
            assertEquals(64, repository.templateContentHash(promptKey).length());
            assertEquals(64, repository.manifestContentHash(promptKey).length());
        }

        AbstractPromptTemplate stateTemplate = repository.findTemplate(SystemPromptKey.STATE_DELTA_EXTRACT).orElseThrow();
        AbstractPromptTemplate summaryTemplate = repository.findTemplate(SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT).orElseThrow();
        assertInstanceOf(StateDeltaExtractPromptTemplate.class, stateTemplate);
        assertInstanceOf(ConversationSummaryExtractPromptTemplate.class, summaryTemplate);
        assertEquals(PromptRenderOutputType.CHAT_MESSAGES, stateTemplate.getRenderOutputType());

        StructuredLlmOutputContract contract =
            repository.findContract(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT).orElseThrow();
        assertTrue(contract.getSchemaJson().contains("\"x-structuredOutputContractKey\""));
        assertTrue(contract.getSchemaJson().contains("\"x-outputTarget\""));
        assertEquals(64, repository.contractContentHash(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT).length());
    }

    @Test
    void shouldFailWhenManifestPromptKeyDoesNotMatchDirectory() {
        assertInvalidCatalog("prompt-catalog-invalid/key-mismatch", SystemPromptKey.RUNTIME_INSTRUCTION_RENDER);
    }

    @Test
    void shouldFailWhenManifestPurposeDoesNotMatchConcreteTemplate() {
        assertInvalidCatalog("prompt-catalog-invalid/purpose-mismatch", SystemPromptKey.RUNTIME_INSTRUCTION_RENDER);
    }

    @Test
    void shouldFailWhenRenderPromptBindsContract() {
        assertInvalidCatalog("prompt-catalog-invalid/render-with-contract", SystemPromptKey.RUNTIME_INSTRUCTION_RENDER);
    }

    @Test
    void shouldFailWhenExtractPromptMissesContract() {
        assertInvalidCatalog("prompt-catalog-invalid/extract-missing-contract", SystemPromptKey.STATE_DELTA_EXTRACT);
    }

    @Test
    void shouldFailWhenContractIsNotSchemaObject() {
        assertInvalidCatalog("prompt-catalog-invalid/contract-wrapper", SystemPromptKey.STATE_DELTA_EXTRACT);
    }

    @Test
    void shouldFailWhenContractMetadataIsMissingOrMismatched() {
        assertInvalidCatalog("prompt-catalog-invalid/contract-missing-key", SystemPromptKey.STATE_DELTA_EXTRACT);
        assertInvalidCatalog("prompt-catalog-invalid/contract-missing-target", SystemPromptKey.STATE_DELTA_EXTRACT);
        assertInvalidCatalog("prompt-catalog-invalid/contract-key-mismatch", SystemPromptKey.STATE_DELTA_EXTRACT);
    }

    @Test
    void shouldFailWhenUntrustedDataVariableMissesSafetyFields() {
        assertInvalidCatalog("prompt-catalog-invalid/untrusted-missing-max", SystemPromptKey.STATE_DELTA_EXTRACT);
        assertInvalidCatalog("prompt-catalog-invalid/untrusted-missing-marker", SystemPromptKey.STATE_DELTA_EXTRACT);
        assertInvalidCatalog("prompt-catalog-invalid/untrusted-instruction-placement", SystemPromptKey.STATE_DELTA_EXTRACT);
    }

    @Test
    void shouldFailWhenTextPromptUntrustedDataBoundaryIsInvalid() {
        assertInvalidCatalog("prompt-catalog-invalid/text-untrusted-no-boundary", SystemPromptKey.SESSION_STATE_RENDER);
        assertInvalidCatalog("prompt-catalog-invalid/text-untrusted-mismatch", SystemPromptKey.SESSION_STATE_RENDER);
        assertInvalidCatalog("prompt-catalog-invalid/text-untrusted-outside", SystemPromptKey.SESSION_STATE_RENDER);
    }

    @Test
    void shouldLoadTextPromptWhenUntrustedDataBoundaryIsValid() {
        ResourceSystemPromptCatalogRepository repository =
            new ResourceSystemPromptCatalogRepository(
                "prompt-catalog/system",
                "test-revision",
                List.of(SystemPromptKey.SESSION_STATE_RENDER)
            );

        assertEquals(
            SystemPromptKey.SESSION_STATE_RENDER,
            repository.findTemplate(SystemPromptKey.SESSION_STATE_RENDER).orElseThrow().getPromptKey()
        );
    }

    @Test
    void shouldFailWhenTemplateUsesUndeclaredPlaceholder() {
        assertInvalidCatalog("prompt-catalog-invalid/undeclared-placeholder", SystemPromptKey.RUNTIME_INSTRUCTION_RENDER);
    }

    @Test
    void shouldNormalizeHashWithUtf8LfAndBomRemoval() {
        String hashWithBom = PromptManifestLoader.sha256Hex("\uFEFFline1\r\nline2\r\n");
        String hashWithoutBom = PromptManifestLoader.sha256Hex("line1\nline2\n");

        assertEquals(hashWithoutBom, hashWithBom);
        assertEquals(64, hashWithBom.length());
        assertNotEquals(PromptManifestLoader.sha256Hex("other-path:line1\nline2\n"), hashWithBom);
    }

    @Test
    void shouldKeepUntrustedDeclaredVariableEvenWhenManifestHasUnusedVariables() {
        ResourceSystemPromptCatalogRepository repository =
            new ResourceSystemPromptCatalogRepository(
                "prompt-catalog/system",
                "test-revision",
                List.of(SystemPromptKey.STATE_DELTA_EXTRACT)
            );

        AbstractPromptTemplate template = repository.findTemplate(SystemPromptKey.STATE_DELTA_EXTRACT).orElseThrow();
        assertTrue(template.getInputVariables().stream().anyMatch(variable ->
            variable.getTrustLevel() == PromptInputTrustLevel.UNTRUSTED_DATA
                && variable.getPlacement() == PromptInputPlacement.DATA_BLOCK
                && variable.getMaxChars() != null
                && variable.getTruncateMarker() != null
        ));
    }

    @Test
    void shouldKeepTestCatalogContractsAlignedWithP2EDesign() throws Exception {
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

    private void assertInvalidCatalog(String basePath, SystemPromptKey promptKey) {
        assertThrows(IllegalStateException.class,
            () -> new ResourceSystemPromptCatalogRepository(basePath, "test-revision", List.of(promptKey)));
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
