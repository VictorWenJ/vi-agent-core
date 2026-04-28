package com.vi.agent.core.infra.provider;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsRequest;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredOutputRequestAdapterTest {

    private final ProviderStructuredOutputCapabilityValidator validator = new ProviderStructuredOutputCapabilityValidator(
        new ProviderStructuredSchemaCompiler()
    );
    private final StructuredOutputRequestAdapter adapter = new StructuredOutputRequestAdapter();

    @Test
    void strictToolCallShouldWriteInternalFunctionTool() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek("https://api.deepseek.com/beta", "deepseek-chat", true)
        );
        ChatCompletionsRequest request = new ChatCompletionsRequest();

        adapter.apply(request, selection);

        String requestJson = JsonUtils.toJson(request);
        assertEquals(1, request.getTools().size());
        assertTrue(requestJson.contains("\"strict\":true"));
        assertTrue(requestJson.contains("\"tool_choice\""));
        assertTrue(requestJson.contains("emit_state_delta"));
        assertTrue(requestJson.contains("\"parameters\""));
        assertFalse(requestJson.contains("x-structuredOutputContractKey"));
    }

    @Test
    void jsonObjectShouldWriteResponseFormatWithoutTool() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
                .preferredStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
                .build(),
            ProviderStructuredOutputCapability.deepSeek()
        );
        ChatCompletionsRequest request = new ChatCompletionsRequest();

        adapter.apply(request, selection);

        String requestJson = JsonUtils.toJson(request);
        assertNull(request.getTools());
        assertTrue(requestJson.contains("\"response_format\""));
        assertTrue(requestJson.contains("\"json_object\""));
    }

    @Test
    void jsonSchemaResponseFormatShouldWriteSchemaView() {
        ProviderStructuredOutputSelection selection = validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
                .preferredStructuredOutputMode(StructuredLlmOutputMode.JSON_SCHEMA_RESPONSE_FORMAT)
                .build(),
            ProviderStructuredOutputCapability.builder()
                .providerName("openai")
                .modelName("gpt-test")
                .supportsStrictToolCall(false)
                .supportsJsonSchemaResponseFormat(true)
                .supportsJsonObject(true)
                .build()
        );
        ChatCompletionsRequest request = new ChatCompletionsRequest();

        adapter.apply(request, selection);

        String requestJson = JsonUtils.toJson(request);
        assertNull(request.getTools());
        assertTrue(requestJson.contains("\"json_schema\""));
        assertTrue(requestJson.contains("\"schema\""));
        assertFalse(requestJson.contains("x-structuredOutputContractKey"));
    }

    @Test
    void disabledSelectionShouldLeaveRequestUntouched() {
        ChatCompletionsRequest request = new ChatCompletionsRequest();

        adapter.apply(request, ProviderStructuredOutputSelection.disabled());

        assertNull(request.getTools());
        assertNull(request.getToolChoice());
        assertNull(request.getResponseFormat());
    }
}
