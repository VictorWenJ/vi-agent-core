package com.vi.agent.core.infra.prompt;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSystemPromptCatalogRepositoryTest {

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

    private void assertInvalidCatalog(String basePath, SystemPromptKey promptKey) {
        assertThrows(IllegalStateException.class,
            () -> new ResourceSystemPromptCatalogRepository(basePath, "test-revision", List.of(promptKey)));
    }
}
