package com.vi.agent.core.infra.provider;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.RunState;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class DeepSeekProviderTest {

    @Test
    void generateShouldParseNormalAssistantResponse() {
        DeepSeekProvider provider = new DeepSeekProvider(
            buildProperties(),
            new FakeExecutor("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}")
        );

        AssistantMessage response = provider.generate(buildContext());
        Assertions.assertEquals("hello", response.getContent());
        Assertions.assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    void generateShouldParseToolCalls() {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"echo_text\",\"arguments\":\"{\\\"text\\\":\\\"hi\\\"}\"}}]}}]}";
        DeepSeekProvider provider = new DeepSeekProvider(buildProperties(), new FakeExecutor(body));

        AssistantMessage response = provider.generate(buildContext());

        Assertions.assertEquals(1, response.getToolCalls().size());
        ToolCall toolCall = response.getToolCalls().get(0);
        Assertions.assertEquals("echo_text", toolCall.getToolName());
        Assertions.assertTrue(toolCall.getArgumentsJson().contains("text"));
    }

    @Test
    void generateShouldThrowWhenProviderFailed() {
        DeepSeekProvider provider = new DeepSeekProvider(
            buildProperties(),
            new DeepSeekHttpExecutor() {
                @Override
                public String post(String url, String apiKey, String body, int timeoutMs) {
                    throw new RuntimeException("network down");
                }

                @Override
                public void postStream(String url, String apiKey, String body, int timeoutMs, java.util.function.Consumer<String> lineConsumer) {
                    throw new RuntimeException("network down");
                }
            }
        );

        AgentRuntimeException exception = Assertions.assertThrows(AgentRuntimeException.class,
            () -> provider.generate(buildContext()));
        Assertions.assertEquals(ErrorCode.PROVIDER_CALL_FAILED, exception.getErrorCode());
    }

    private DeepSeekProperties buildProperties() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setModel("deepseek-chat");
        return properties;
    }

    private AgentRunContext buildContext() {
        ConversationTranscript transcript = new ConversationTranscript("session-1", "conv-1");
        UserMessage userMessage = new UserMessage("msg-1", "hi");
        transcript.appendMessage(userMessage);

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.builder()
            .name("echo_text")
            .description("echo")
            .parametersJson("{}")
            .build());

        return new AgentRunContext(
            "trace-1",
            "run-1",
            "session-1",
            "conv-1",
            "turn-1",
            "hi",
            List.of(userMessage),
            tools,
            transcript,
            RunState.STARTED
        );
    }

    private static class FakeExecutor implements DeepSeekHttpExecutor {

        private final String responseBody;

        private FakeExecutor(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public String post(String url, String apiKey, String body, int timeoutMs) {
            return responseBody;
        }

        @Override
        public void postStream(String url, String apiKey, String body, int timeoutMs, java.util.function.Consumer<String> lineConsumer) {
            lineConsumer.accept("data: " + responseBody);
            lineConsumer.accept("data: [DONE]");
        }
    }
}
