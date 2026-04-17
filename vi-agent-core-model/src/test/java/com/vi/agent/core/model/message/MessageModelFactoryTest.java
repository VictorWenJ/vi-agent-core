package com.vi.agent.core.model.message;

import com.vi.agent.core.model.tool.ToolCall;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageModelFactoryTest {

    @Test
    void createShouldGenerateMessageIdWhenMissing() {
        UserMessage message = UserMessage.create(null, "turn-1", "hello");

        assertNotNull(message.getMessageId());
        assertTrue(message.getMessageId().startsWith("msg-"));
        assertEquals("turn-1", message.getTurnId());
        assertEquals("hello", message.getContent());
    }

    @Test
    void restoreShouldKeepMessageIdentityAndCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-17T10:00:00Z");
        UserMessage message = UserMessage.restore("msg-1", "turn-1", "restored", createdAt);

        assertEquals("msg-1", message.getMessageId());
        assertEquals("turn-1", message.getTurnId());
        assertEquals("restored", message.getContent());
        assertEquals(createdAt, message.getCreatedAt());
    }

    @Test
    void assistantMessageShouldKeepToolCallsAndExposeReadOnlyView() {
        ToolCall toolCall = ToolCall.builder()
            .toolCallId("call-1")
            .toolName("echo_text")
            .argumentsJson("{\"text\":\"hello\"}")
            .turnId("turn-1")
            .build();
        List<ToolCall> source = new ArrayList<>();
        source.add(toolCall);

        AssistantMessage message = AssistantMessage.create("msg-2", "turn-1", "plan", source);
        source.clear();

        assertEquals(1, message.getToolCalls().size());
        assertEquals("call-1", message.getToolCalls().get(0).getToolCallId());
        assertThrows(UnsupportedOperationException.class, () -> message.getToolCalls().add(toolCall));
    }

    @Test
    void toolExecutionMessageFactoriesShouldKeepTurnAndToolCallIdentity() {
        Instant createdAt = Instant.parse("2026-04-17T10:01:00Z");

        ToolExecutionMessage created = ToolExecutionMessage.create(
            "msg-3",
            "turn-2",
            "tool-call-1",
            "get_time",
            "2026-04-17T10:01:00Z"
        );
        ToolExecutionMessage restored = ToolExecutionMessage.restore(
            "msg-4",
            "turn-3",
            "tool-call-2",
            "echo_text",
            "hello",
            createdAt
        );

        assertEquals("turn-2", created.getTurnId());
        assertEquals("tool-call-1", created.getToolCallId());
        assertEquals("turn-3", restored.getTurnId());
        assertEquals("tool-call-2", restored.getToolCallId());
        assertEquals(createdAt, restored.getCreatedAt());
    }

    @Test
    void coreMessageTypesShouldNotExposePublicConstructors() {
        assertEquals(0, UserMessage.class.getConstructors().length);
        assertEquals(0, AssistantMessage.class.getConstructors().length);
        assertEquals(0, ToolExecutionMessage.class.getConstructors().length);
    }
}

