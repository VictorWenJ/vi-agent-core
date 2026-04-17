package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.infra.provider.http.HttpRequestOptions;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekChatProviderTest {

    @Test
    void generateShouldParseNormalAssistantResponse() {
        FakeHttpExecutor executor = new FakeHttpExecutor();
        executor.postResponse = """
            {"choices":[{"message":{"role":"assistant","content":"normal-answer","tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_time","arguments":"{}"}}]}}]}
            """;

        DeepSeekChatProvider provider = new DeepSeekChatProvider(defaultProperties(), executor);

        AssistantMessage message = provider.generate(newRunContext());

        assertEquals("normal-answer", message.getContent());
        assertEquals(1, message.getToolCalls().size());
        ToolCall toolCall = message.getToolCalls().get(0);
        assertEquals("call_1", toolCall.getToolCallId());
        assertEquals("get_time", toolCall.getToolName());
        assertEquals("{}", toolCall.getArgumentsJson());
    }

    @Test
    void generateStreamingShouldEmitTokenChunks() {
        FakeHttpExecutor executor = new FakeHttpExecutor();
        executor.streamLines = List.of(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}",
            "data: [DONE]"
        );
        DeepSeekChatProvider provider = new DeepSeekChatProvider(defaultProperties(), executor);

        List<String> chunks = new ArrayList<>();
        AssistantMessage message = provider.generateStreaming(newRunContext(), chunks::add);

        assertEquals("Hello", message.getContent());
        assertEquals(List.of("Hel", "lo"), chunks);
        assertTrue(message.getToolCalls().isEmpty());
    }

    @Test
    void generateStreamingShouldAggregateToolCallFragments() {
        FakeHttpExecutor executor = new FakeHttpExecutor();
        executor.streamLines = List.of(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"weather\",\"arguments\":\"{\\\"city\\\":\\\"\"}}]}}]}",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"type\":\"function\",\"function\":{\"arguments\":\"Shanghai\\\"}\"}}]}}]}",
            "data: [DONE]"
        );
        DeepSeekChatProvider provider = new DeepSeekChatProvider(defaultProperties(), executor);

        AssistantMessage message = provider.generateStreaming(newRunContext(), chunk -> {
        });

        assertEquals("", message.getContent());
        assertEquals(1, message.getToolCalls().size());
        ToolCall toolCall = message.getToolCalls().get(0);
        assertEquals("call_1", toolCall.getToolCallId());
        assertEquals("weather", toolCall.getToolName());
        assertEquals("{\"city\":\"Shanghai\"}", toolCall.getArgumentsJson());
    }

    private AgentRunContext newRunContext() {
        ConversationTranscript transcript = ConversationTranscript.start("session-1", "conversation-1");
        return new AgentRunContext(
            "trace-1",
            "run-1",
            "session-1",
            "conversation-1",
            "turn-1",
            "hello",
            List.of(UserMessage.create("msg-1", "turn-1", "hello")),
            List.of(),
            transcript,
            AgentRunState.STARTED
        );
    }

    private DeepSeekProperties defaultProperties() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://example.com");
        properties.setChatPath("/chat/completions");
        properties.setModel("deepseek-chat");
        return properties;
    }

    private static final class FakeHttpExecutor implements LlmHttpExecutor {

        private String postResponse;
        private List<String> streamLines = List.of();

        @Override
        public String post(String url, Map<String, String> headers, String body, HttpRequestOptions options) {
            return postResponse;
        }

        @Override
        public void postStream(
            String url,
            Map<String, String> headers,
            String body,
            HttpRequestOptions options,
            Consumer<String> lineConsumer
        ) {
            for (String line : streamLines) {
                lineConsumer.accept(line);
            }
        }
    }
}
