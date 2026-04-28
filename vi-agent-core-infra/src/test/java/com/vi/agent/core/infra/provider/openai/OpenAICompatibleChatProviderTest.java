package com.vi.agent.core.infra.provider.openai;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.provider.ProviderStructuredOutputTestSupport;
import com.vi.agent.core.infra.provider.ProviderStructuredOutputCapability;
import com.vi.agent.core.infra.provider.base.OpenAICompatibleChatProvider;
import com.vi.agent.core.infra.provider.http.HttpRequestOptions;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsRequest;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAICompatibleChatProviderTest {

    @Test
    void ordinaryChatRequestShouldNotCarryInternalStructuredOutputFunction() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithContent("hello"));
        TestProvider provider = new TestProvider(executor);

        provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "hello")))
            .build());

        assertFalse(executor.body.contains("emit_state_delta"));
        assertFalse(executor.body.contains("\"response_format\""));
        assertFalse(executor.body.contains("\"tool_choice\""));
    }

    @Test
    void strictStructuredRequestShouldMapProviderToolCallFields() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithToolCall(
            "emit_state_delta",
            "{\"taskGoalOverride\":\"new goal\"}"
        ));
        TestProvider provider = new TestProvider(executor, "https://api.deepseek.com/beta", true);

        ModelResponse response = provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "extract")))
            .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
            .build());

        assertTrue(executor.body.contains("\"tools\""));
        assertTrue(executor.body.contains("\"parameters\""));
        assertTrue(executor.body.contains("\"strict\":true"));
        assertTrue(executor.body.contains("\"tool_choice\""));
        assertTrue(response.getStructuredOutputChannelResult().getSuccess());
        assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    void jsonObjectStructuredRequestShouldMapResponseFormat() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithContent("{\"taskGoalOverride\":\"new goal\"}"));
        TestProvider provider = new TestProvider(executor);

        ModelResponse response = provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "extract")))
            .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
            .build());

        assertTrue(executor.body.contains("\"response_format\""));
        assertTrue(executor.body.contains("\"json_object\""));
        assertFalse(executor.body.contains("emit_state_delta"));
        assertTrue(response.getStructuredOutputChannelResult().getSuccess());
    }

    @Test
    void selectedModeFailureShouldNotRetryOrSilentlyDowngrade() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithContent("{}"));
        TestProvider provider = new TestProvider(executor, "https://api.deepseek.com/beta", true);

        ModelResponse response = provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "extract")))
            .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
            .build());

        assertEquals(1, executor.postCount);
        assertFalse(response.getStructuredOutputChannelResult().getSuccess());
        assertTrue(response.getStructuredOutputChannelResult().getFailureReason().contains("tool_call"));
        assertEquals(0, response.getStructuredOutputChannelResult().getRetryCount());
    }

    @Test
    void providerRequestFailureShouldNotRetryOrSilentlyDowngrade() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithContent("{}"));
        executor.throwOnPost = true;
        TestProvider provider = new TestProvider(executor, "https://api.deepseek.com/beta", true);

        assertThrows(AgentRuntimeException.class, () -> provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "extract")))
            .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
            .build()));

        assertEquals(1, executor.postCount);
    }

    @Test
    void structuredSelectionShouldBeCalculatedOncePerGenerateCall() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithToolCall(
            "emit_state_delta",
            "{\"taskGoalOverride\":\"new goal\"}"
        ));
        CountingProvider provider = new CountingProvider(executor);

        provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "extract")))
            .structuredOutputContract(ProviderStructuredOutputTestSupport.strictCompatibleStateDeltaContract())
            .build());

        assertEquals(1, provider.capabilityCallCount);
    }

    @Test
    void structuredOutputWithBusinessToolsShouldFailFastInP2E3() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithContent("{}"));
        TestProvider provider = new TestProvider(executor);

        assertThrows(AgentRuntimeException.class, () -> provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "extract")))
            .tools(List.of(ToolDefinition.builder()
                .name("business_tool")
                .description("business tool")
                .parametersJson("{\"type\":\"object\",\"properties\":{}}")
                .build()))
            .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
            .build()));

        assertEquals(0, executor.postCount);
    }

    @Test
    void subclassRequestCustomizationShouldStillRunWithStructuredSelection() {
        RecordingHttpExecutor executor = new RecordingHttpExecutor(responseWithContent("{\"taskGoalOverride\":\"new goal\"}"));
        CustomizingProvider provider = new CustomizingProvider(executor);

        provider.generate(ModelRequest.builder()
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "extract")))
            .structuredOutputContract(ProviderStructuredOutputTestSupport.nonStrictCompatibleStateDeltaContract())
            .build());

        assertTrue(executor.body.contains("\"thinking_type\":\"disabled\""));
    }

    private static String responseWithContent(String content) {
        return """
            {
              "id": "resp-1",
              "model": "deepseek-chat",
              "choices": [
                {
                  "index": 0,
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": %s
                  }
                }
              ]
            }
            """.formatted(JsonUtils.toJson(content));
    }

    private static String responseWithToolCall(String functionName, String arguments) {
        return """
            {
              "id": "resp-1",
              "model": "deepseek-chat",
              "choices": [
                {
                  "index": 0,
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call-1",
                        "type": "function",
                        "function": {
                          "name": %s,
                          "arguments": %s
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """.formatted(JsonUtils.toJson(functionName), JsonUtils.toJson(arguments));
    }

    private static class TestProvider extends OpenAICompatibleChatProvider {

        private TestProvider(RecordingHttpExecutor executor) {
            this(executor, "https://api.deepseek.com", false);
        }

        private TestProvider(RecordingHttpExecutor executor, String baseUrl, boolean strictToolCallEnabled) {
            this.httpExecutor = executor;
            this.messageProjector = new OpenAICompatibleMessageProjector();
            this.baseUrl = baseUrl;
            this.strictToolCallEnabled = strictToolCallEnabled;
        }

        private final String baseUrl;

        private final boolean strictToolCallEnabled;

        @Override
        protected String providerName() {
            return "DeepSeek";
        }

        @Override
        protected String providerKey() {
            return "deepseek";
        }

        @Override
        protected String baseUrl() {
            return baseUrl;
        }

        @Override
        protected String chatPath() {
            return "/chat/completions";
        }

        @Override
        protected String apiKey() {
            return "test-key";
        }

        @Override
        protected String model() {
            return "deepseek-chat";
        }

        @Override
        protected int connectTimeoutMs() {
            return 1000;
        }

        @Override
        protected int readTimeoutMs() {
            return 1000;
        }

        @Override
        protected ProviderStructuredOutputCapability structuredOutputCapability() {
            return ProviderStructuredOutputCapability.deepSeek(baseUrl(), model(), strictToolCallEnabled);
        }
    }

    private static final class CountingProvider extends TestProvider {

        private int capabilityCallCount;

        private CountingProvider(RecordingHttpExecutor executor) {
            super(executor, "https://api.deepseek.com/beta", true);
        }

        @Override
        protected ProviderStructuredOutputCapability structuredOutputCapability() {
            capabilityCallCount++;
            return super.structuredOutputCapability();
        }
    }

    private static final class CustomizingProvider extends TestProvider {

        private CustomizingProvider(RecordingHttpExecutor executor) {
            super(executor, "https://api.deepseek.com", false);
        }

        @Override
        protected void customizeRequest(ChatCompletionsRequest request, ModelRequest modelRequest, boolean stream) {
            request.setThinkingType("disabled");
        }
    }

    private static final class RecordingHttpExecutor implements LlmHttpExecutor {

        private final String responseBody;

        private String body;

        private int postCount;

        private boolean throwOnPost;

        private RecordingHttpExecutor(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public String post(String url, Map<String, String> headers, String body, HttpRequestOptions options) {
            this.body = body;
            this.postCount++;
            if (throwOnPost) {
                throw new IllegalStateException("provider failed");
            }
            return responseBody;
        }

        @Override
        public void postStream(
            String url,
            Map<String, String> headers,
            String body,
            HttpRequestOptions options,
            Consumer<String> lineConsumer
        ) {
            throw new UnsupportedOperationException("stream is not used in this test");
        }
    }
}
