package com.vi.agent.core.infra.provider.openai;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsMessage;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAICompatibleMessageProjectorTest {

    private final OpenAICompatibleMessageProjector projector = new OpenAICompatibleMessageProjector();

    @Test
    void projectShouldMapLegalToolChainToUserAssistantTool() {
        List<Message> messages = List.of(
            UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "鐜板湪鍑犵偣"),
            buildAssistant("msg-assistant-1", "tcr-1", "call-1"),
            ToolMessage.create(
                "msg-tool-1",
                "conv-1",
                "sess-1",
                "turn-1",
                "run-1",
                3L,
                "2026-04-23T00:00:00+08:00",
                "tcr-1",
                "call-1",
                "get_time",
                ToolExecutionStatus.SUCCEEDED,
                null,
                null,
                1L,
                "{}"
            )
        );

        List<ChatCompletionsMessage> projected = projector.project(messages);

        assertEquals(3, projected.size());
        assertEquals("user", projected.get(0).getRole());
        assertEquals("assistant", projected.get(1).getRole());
        assertEquals("tool", projected.get(2).getRole());
        assertEquals(1, projected.get(1).getToolCalls().size());
        assertEquals("call-1", projected.get(1).getToolCalls().get(0).getId());
        assertEquals("call-1", projected.get(2).getToolCallId());
    }

    @Test
    void projectShouldFailFastWhenToolMessageHasNoPendingAssistantToolCall() {
        List<Message> messages = List.of(
            UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "鐜板湪鍑犵偣"),
            ToolMessage.create(
                "msg-tool-1",
                "conv-1",
                "sess-1",
                "turn-1",
                "run-1",
                2L,
                "2026-04-23T00:00:00+08:00",
                "tcr-1",
                "call-1",
                "get_time",
                ToolExecutionStatus.SUCCEEDED,
                null,
                null,
                1L,
                "{}"
            )
        );

        AgentRuntimeException exception = assertThrows(AgentRuntimeException.class, () -> projector.project(messages));
        assertEquals(ErrorCode.INVALID_MODEL_CONTEXT_MESSAGE, exception.getErrorCode());
    }

    private AssistantMessage buildAssistant(String messageId, String toolCallRecordId, String toolCallId) {
        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId(toolCallRecordId)
            .toolCallId(toolCallId)
            .assistantMessageId(messageId)
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .callIndex(0)
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build();
        return AssistantMessage.create(
            messageId,
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            2L,
            "鎴戞潵鏌ヨ鏃堕棿",
            List.of(toolCall),
            FinishReason.TOOL_CALL,
            UsageInfo.empty()
        );
    }
}

