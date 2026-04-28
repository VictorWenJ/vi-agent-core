package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderStructuredSchemaCompilerTest {

    private final ProviderStructuredSchemaCompiler compiler = new ProviderStructuredSchemaCompiler();

    @Test
    void strictCompatibleSchemaShouldCompileAndStripXMetadata() {
        StructuredLlmOutputContract contract = ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract();
        String originalSchemaJson = contract.getSchemaJson();

        ProviderStructuredSchemaCompileResult result = compiler.compile(
            contract,
            StructuredLlmOutputMode.STRICT_TOOL_CALL,
            "deepseek",
            "deepseek-chat"
        );

        assertTrue(result.getAvailable());
        assertNotNull(result.getProviderSchemaView());
        assertFalse(result.getProviderSchemaViewJson().contains("\"x-structuredOutputContractKey\""));
        assertFalse(result.getProviderSchemaViewJson().contains("\"x-outputTarget\""));
        assertFalse(result.getProviderSchemaViewJson().contains("\"x-description\""));
        assertTrue(result.getProviderSchemaViewJson().contains("\"additionalProperties\":false"));
        assertEquals(originalSchemaJson, contract.getSchemaJson());
    }

    @Test
    void nonStrictCompatibleSchemaShouldReturnUnavailable() {
        ProviderStructuredSchemaCompileResult result = compiler.compile(
            ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract(),
            StructuredLlmOutputMode.STRICT_TOOL_CALL,
            "deepseek",
            "deepseek-chat"
        );

        assertFalse(result.getAvailable());
        assertTrue(result.getFailureReason().contains("type array"));
    }

    @Test
    void complexOneOfSchemaShouldReturnUnavailableForStrictToolCall() {
        ProviderStructuredSchemaCompileResult result = compiler.compile(
            ProviderStructuredOutputTestSupport.oneOfSummaryContract(),
            StructuredLlmOutputMode.STRICT_TOOL_CALL,
            "deepseek",
            "deepseek-chat"
        );

        assertFalse(result.getAvailable());
        assertTrue(result.getFailureReason().contains("oneOf"));
    }

    @Test
    void invalidSchemaShouldReturnUnavailableInsteadOfThrowing() {
        StructuredLlmOutputContract contract = StructuredLlmOutputContract.builder()
            .schemaJson("{not-json")
            .build();

        ProviderStructuredSchemaCompileResult result = compiler.compile(
            contract,
            StructuredLlmOutputMode.STRICT_TOOL_CALL,
            "deepseek",
            "deepseek-chat"
        );

        assertFalse(result.getAvailable());
        assertTrue(result.getFailureReason().contains("schema"));
    }
}
