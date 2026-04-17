package com.vi.agent.core.model.transcript;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConversationTranscriptFactoryTest {

    @Test
    void startShouldCreateTranscriptWithSessionAndConversationIds() {
        ConversationTranscript transcript = ConversationTranscript.start("session-1", "conversation-1");

        assertEquals("session-1", transcript.getSessionId());
        assertEquals("conversation-1", transcript.getConversationId());
        assertNotNull(transcript.getUpdatedAt());
    }

    @Test
    void restoreShouldKeepTurnAndMessageIdentityChains() {
        Instant updatedAt = Instant.parse("2026-04-17T10:10:00Z");
        Message userMessage = UserMessage.restore(
            "msg-1",
            "turn-1",
            "hello",
            Instant.parse("2026-04-17T10:00:00Z")
        );
        Message assistantMessage = AssistantMessage.restore(
            "msg-2",
            "turn-1",
            "world",
            List.of(),
            Instant.parse("2026-04-17T10:00:01Z")
        );
        ToolCall toolCall = ToolCall.builder()
            .toolCallId("tool-call-1")
            .toolName("echo_text")
            .argumentsJson("{\"text\":\"hello\"}")
            .turnId("turn-1")
            .build();
        ToolResult toolResult = ToolResult.builder()
            .toolCallId("tool-call-1")
            .toolName("echo_text")
            .turnId("turn-1")
            .success(true)
            .output("hello")
            .build();

        ConversationTranscript transcript = ConversationTranscript.restore(
            "session-1",
            "conversation-1",
            "trace-1",
            "run-1",
            List.of(userMessage, assistantMessage),
            List.of(toolCall),
            List.of(toolResult),
            updatedAt
        );

        assertEquals("trace-1", transcript.getTraceId());
        assertEquals("run-1", transcript.getRunId());
        assertEquals(updatedAt, transcript.getUpdatedAt());
        assertEquals("msg-1", transcript.getMessages().get(0).getMessageId());
        assertEquals("turn-1", transcript.getMessages().get(0).getTurnId());
        assertEquals("msg-2", transcript.getMessages().get(1).getMessageId());
        assertEquals("turn-1", transcript.getMessages().get(1).getTurnId());
        assertEquals("tool-call-1", transcript.getToolCalls().get(0).getToolCallId());
        assertEquals("turn-1", transcript.getToolCalls().get(0).getTurnId());
        assertEquals("tool-call-1", transcript.getToolResults().get(0).getToolCallId());
        assertEquals("turn-1", transcript.getToolResults().get(0).getTurnId());
    }
}

