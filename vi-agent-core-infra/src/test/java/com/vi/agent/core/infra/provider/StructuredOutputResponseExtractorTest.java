package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsChoice;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsFunction;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsMessage;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsResponse;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsToolCall;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredOutputResponseExtractorTest {

    private final ProviderStructuredOutputCapabilityValidator validator = new ProviderStructuredOutputCapabilityValidator(
        new ProviderStructuredSchemaCompiler()
    );
    private final StructuredOutputResponseExtractor extractor = new StructuredOutputResponseExtractor();

    @Test
    void strictToolCallArgumentsShouldNormalizeToStructuredOutput() {
        ProviderStructuredOutputSelection selection = strictSelection();

        StructuredOutputChannelResult result = extractor.extract(
            responseWithToolCall("emit_state_delta", "{\"taskGoalOverride\":\"new goal\"}", null),
            selection,
            "deepseek",
            "deepseek-chat"
        );

        assertTrue(result.getSuccess());
        assertEquals(StructuredLlmOutputMode.STRICT_TOOL_CALL, result.getActualStructuredOutputMode());
        assertEquals("{\"taskGoalOverride\":\"new goal\"}", result.getOutput().getOutputJson());
        assertEquals("deepseek", result.getOutput().getProviderName());
        assertEquals("deepseek-chat", result.getOutput().getModelName());
        assertEquals("resp-1", result.getOutput().getProviderResponseId());
        assertEquals(0, result.getRetryCount());
    }

    @Test
    void strictMissingToolCallShouldReturnFailure() {
        StructuredOutputChannelResult result = extractor.extract(
            responseWithContent("{}"),
            strictSelection(),
            "deepseek",
            "deepseek-chat"
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains("tool_call"));
        assertEquals(0, result.getRetryCount());
    }

    @Test
    void strictWrongFunctionNameShouldReturnFailure() {
        StructuredOutputChannelResult result = extractor.extract(
            responseWithToolCall("other_function", "{\"taskGoalOverride\":\"new goal\"}", null),
            strictSelection(),
            "deepseek",
            "deepseek-chat"
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains("function"));
    }

    @Test
    void strictArgumentsMustBeJsonObject() {
        StructuredOutputChannelResult result = extractor.extract(
            responseWithToolCall("emit_state_delta", "[]", null),
            strictSelection(),
            "deepseek",
            "deepseek-chat"
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains("JSON object"));
    }

    @Test
    void jsonObjectContentShouldNormalizeAndUnwrapMarkdownFence() {
        ProviderStructuredOutputSelection selection = jsonObjectSelection();

        StructuredOutputChannelResult result = extractor.extract(
            responseWithContent("""
                ```json
                {"taskGoalOverride":"new goal"}
                ```
                """),
            selection,
            "deepseek",
            "deepseek-chat"
        );

        assertTrue(result.getSuccess());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, result.getActualStructuredOutputMode());
        assertEquals("{\"taskGoalOverride\":\"new goal\"}", result.getOutput().getOutputJson());
    }

    @Test
    void jsonObjectContentMustBeJsonObject() {
        assertJsonObjectFailure("[]", "JSON object");
        assertJsonObjectFailure("plain text", "JSON");
    }

    private ProviderStructuredOutputSelection strictSelection() {
        return validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek("https://api.deepseek.com/beta", "deepseek-chat", true)
        );
    }

    private ProviderStructuredOutputSelection jsonObjectSelection() {
        return validator.select(
            ModelRequest.builder()
                .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
                .build(),
            ProviderStructuredOutputCapability.deepSeek()
        );
    }

    private void assertJsonObjectFailure(String content, String reasonPart) {
        StructuredOutputChannelResult result = extractor.extract(
            responseWithContent(content),
            jsonObjectSelection(),
            "deepseek",
            "deepseek-chat"
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains(reasonPart));
        assertEquals(0, result.getRetryCount());
    }

    private ChatCompletionsResponse responseWithContent(String content) {
        ChatCompletionsMessage message = new ChatCompletionsMessage();
        message.setContent(content);
        return response(message);
    }

    private ChatCompletionsResponse responseWithToolCall(String functionName, String arguments, String content) {
        ChatCompletionsFunction function = new ChatCompletionsFunction();
        function.setName(functionName);
        function.setArguments(arguments);
        ChatCompletionsToolCall toolCall = new ChatCompletionsToolCall();
        toolCall.setId("call-1");
        toolCall.setType("function");
        toolCall.setFunction(function);
        ChatCompletionsMessage message = new ChatCompletionsMessage();
        message.setContent(content);
        message.setToolCalls(List.of(toolCall));
        return response(message);
    }

    private ChatCompletionsResponse response(ChatCompletionsMessage message) {
        ChatCompletionsChoice choice = new ChatCompletionsChoice();
        choice.setMessage(message);
        ChatCompletionsResponse response = new ChatCompletionsResponse();
        response.setId("resp-1");
        response.setModel("deepseek-chat");
        response.setChoices(List.of(choice));
        return response;
    }
}
