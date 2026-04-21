package com.vi.agent.core.infra.provider.base;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsRequest;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAICompatibleChatProviderModelContextTest {

    @Test
    void buildRequestShouldRejectToolCallMessageInModelContext() {
        TestOpenAICompatibleChatProvider provider = new TestOpenAICompatibleChatProvider();
        ModelRequest request = ModelRequest.builder()
            .runId("run-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .messages(List.of(
                ToolCallMessage.create("msg-tool-call", "turn-1", 1L, "call-1", "get_time", "{}")
            ))
            .tools(List.of())
            .build();

        AgentRuntimeException exception = assertThrows(
            AgentRuntimeException.class,
            () -> provider.buildRequestForTest(request)
        );

        assertEquals(ErrorCode.INVALID_MODEL_CONTEXT_MESSAGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("ToolCallMessage is an internal fact and must not be sent to provider"));
    }

    @Test
    void buildRequestShouldMapLegalToolCallSequenceWithoutDuplicateAssistantToolCalls() {
        TestOpenAICompatibleChatProvider provider = new TestOpenAICompatibleChatProvider();
        String toolCallId = "call-1";
        ModelRequest request = ModelRequest.builder()
            .runId("run-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .messages(List.of(
                UserMessage.create("msg-user", "turn-1", 1L, "现在是什么时间"),
                AssistantMessage.create(
                    "msg-assistant-tool",
                    "turn-1",
                    2L,
                    "我来查询时间",
                    List.of(ModelToolCall.builder()
                        .toolCallId(toolCallId)
                        .toolName("get_time")
                        .argumentsJson("{}")
                        .build())
                ),
                ToolResultMessage.create(
                    "msg-tool-result",
                    "turn-1",
                    3L,
                    toolCallId,
                    "get_time",
                    true,
                    "2026-04-21T16:00:00+08:00",
                    null,
                    null,
                    1L
                )
            ))
            .tools(List.of())
            .build();

        ChatCompletionsRequest apiRequest = provider.buildRequestForTest(request);

        assertEquals(3, apiRequest.getMessages().size());
        assertEquals("user", apiRequest.getMessages().get(0).getRole());
        assertEquals("assistant", apiRequest.getMessages().get(1).getRole());
        assertEquals("tool", apiRequest.getMessages().get(2).getRole());

        assertEquals(1, apiRequest.getMessages().get(1).getToolCalls().size());
        assertEquals(toolCallId, apiRequest.getMessages().get(1).getToolCalls().get(0).getId());

        long assistantToolCallMessageCount = apiRequest.getMessages().stream()
            .filter(message -> "assistant".equals(message.getRole()))
            .filter(message -> message.getToolCalls() != null && !message.getToolCalls().isEmpty())
            .count();
        assertEquals(1, assistantToolCallMessageCount);
    }

    private static final class TestOpenAICompatibleChatProvider extends OpenAICompatibleChatProvider {

        private ChatCompletionsRequest buildRequestForTest(ModelRequest request) {
            return buildRequest(request, false);
        }

        @Override
        protected String providerName() {
            return "test-provider";
        }

        @Override
        protected String providerKey() {
            return "test";
        }

        @Override
        protected String baseUrl() {
            return "https://example.com";
        }

        @Override
        protected String chatPath() {
            return "/chat/completions";
        }

        @Override
        protected String apiKey() {
            return "key";
        }

        @Override
        protected String model() {
            return "test-model";
        }

        @Override
        protected int connectTimeoutMs() {
            return 1000;
        }

        @Override
        protected int readTimeoutMs() {
            return 1000;
        }
    }
}

