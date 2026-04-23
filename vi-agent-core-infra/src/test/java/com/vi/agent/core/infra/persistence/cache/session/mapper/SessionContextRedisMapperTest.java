package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionContextSnapshotDocument;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SessionContextRedisMapperTest {

    private final SessionContextRedisMapper mapper = new SessionContextRedisMapper();

    @Test
    void toDocumentShouldKeepAssistantToolCallsAndToolMessageFields() {
        List<Message> messages = buildMessages();
        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .messages(messages)
            .updatedAt(Instant.now())
            .build();

        SessionContextSnapshotDocument document = mapper.toDocument(snapshot);

        assertEquals("sess-1", document.getSessionId());
        assertEquals("conv-1", document.getConversationId());
        assertEquals(3, document.getMessageCount());
        assertEquals(1, document.getSnapshotVersion());
    }

    @Test
    void toModelShouldRestoreToolChainInContextMessages() {
        List<Message> messages = buildMessages();
        SessionStateSnapshot original = SessionStateSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .messages(messages)
            .updatedAt(Instant.now())
            .build();
        SessionContextSnapshotDocument document = mapper.toDocument(original);

        SessionStateSnapshot restored = mapper.toModel(document);

        assertEquals(3, restored.getMessages().size());
        Message restoredAssistant = restored.getMessages().get(1);
        AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, restoredAssistant);
        assertEquals(1, assistantMessage.getToolCalls().size());
        assertEquals("tcr-1", assistantMessage.getToolCalls().get(0).getToolCallRecordId());
        assertEquals("call-1", assistantMessage.getToolCalls().get(0).getToolCallId());

        Message restoredTool = restored.getMessages().get(2);
        ToolMessage toolMessage = assertInstanceOf(ToolMessage.class, restoredTool);
        assertEquals("tcr-1", toolMessage.getToolCallRecordId());
        assertEquals("call-1", toolMessage.getToolCallId());
        assertEquals(ToolExecutionStatus.SUCCEEDED, toolMessage.getExecutionStatus());
    }

    private List<Message> buildMessages() {
        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant-1")
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

        return List.of(
            UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "鐜板湪鍑犵偣"),
            AssistantMessage.create(
                "msg-assistant-1",
                "conv-1",
                "sess-1",
                "turn-1",
                "run-1",
                2L,
                "鎴戞潵鏌ヨ鏃堕棿",
                List.of(toolCall),
                FinishReason.TOOL_CALL,
                UsageInfo.empty()
            ),
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
    }
}

