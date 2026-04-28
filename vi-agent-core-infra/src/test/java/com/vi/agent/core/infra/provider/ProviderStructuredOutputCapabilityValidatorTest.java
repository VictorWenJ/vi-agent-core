package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderStructuredOutputCapabilityValidatorTest {

    private final ProviderStructuredOutputCapabilityValidator validator = new ProviderStructuredOutputCapabilityValidator(
        new ProviderStructuredSchemaCompiler()
    );

    @Test
    void deepSeekBetaEndpointAndStrictEnabledShouldSelectStrictToolCall() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek("https://api.deepseek.com/beta", "deepseek-chat", true)
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.STRICT_TOOL_CALL, selection.getSelectedStructuredOutputMode());
        assertEquals("emit_state_delta", selection.getFunctionName());
        assertNotNull(selection.getProviderSchemaView());
        assertEquals(0, selection.getRetryCount());
    }

    @Test
    void deepSeekNormalEndpointShouldNotSelectStrictToolCallByDefault() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek("https://api.deepseek.com", "deepseek-chat", false)
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, selection.getSelectedStructuredOutputMode());
        assertEquals(0, selection.getRetryCount());
    }

    @Test
    void deepSeekNormalEndpointWithStrictEnabledShouldFallbackBeforeRequest() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek("https://api.deepseek.com", "deepseek-chat", true)
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, selection.getSelectedStructuredOutputMode());
        assertTrue(selection.getFailureReason().contains("beta"));
    }

    @Test
    void deepSeekBetaEndpointWithStrictDisabledShouldFallbackBeforeRequest() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek("https://api.deepseek.com/beta", "deepseek-chat", false)
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, selection.getSelectedStructuredOutputMode());
    }

    @Test
    void deepSeekStrictIncompatibleSchemaShouldSelectJsonObjectBeforeRequest() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek("https://api.deepseek.com/beta", "deepseek-chat", true)
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, selection.getSelectedStructuredOutputMode());
        assertEquals(0, selection.getRetryCount());
    }

    @Test
    void jsonObjectOnlyProviderShouldSelectJsonObject() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.jsonObjectOnly("test-provider", "test-model")
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, selection.getSelectedStructuredOutputMode());
    }

    @Test
    void providerWithJsonSchemaSupportShouldSelectJsonSchemaBeforeJsonObject() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.builder()
                .providerName("openai")
                .modelName("gpt-test")
                .supportsStrictToolCall(false)
                .supportsJsonSchemaResponseFormat(true)
                .supportsJsonObject(true)
                .build()
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.JSON_SCHEMA_RESPONSE_FORMAT, selection.getSelectedStructuredOutputMode());
    }

    @Test
    void requestWithoutContractShouldNotEnableStructuredOutput() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder().build(),
            ProviderStructuredOutputCapability.deepSeek()
        );

        assertFalse(selection.getEnabled());
    }

    @Test
    void preferredJsonObjectShouldBeSelectedWhenAvailable() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .preferredStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
                .structuredOutputFunctionName("custom_emit")
                .build(),
            ProviderStructuredOutputCapability.deepSeek()
        );

        assertTrue(selection.getEnabled());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, selection.getSelectedStructuredOutputMode());
        assertEquals("custom_emit", selection.getFunctionName());
    }
}
