package com.vi.agent.core.infra.persistence.mapper;

import com.vi.agent.core.infra.persistence.entity.TranscriptEntity;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.ToolExecutionMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisTranscriptMapperTest {

    @Test
    void toEntityAndToModelShouldKeepTurnIdForMessagesAndTools() {
        RedisTranscriptMapper mapper = new RedisTranscriptMapper();

        ConversationTranscript transcript = ConversationTranscript.start("session-1", "conversation-1");
        transcript.setTraceId("trace-1");
        transcript.setRunId("run-1");
        transcript.appendMessage(UserMessage.restore("msg-1", "turn-1", "hello", Instant.parse("2026-04-17T10:00:00Z")));
        transcript.appendMessage(AssistantMessage.restore("msg-2", "turn-1", "world", List.of(), Instant.parse("2026-04-17T10:00:01Z")));
        transcript.appendMessage(ToolExecutionMessage.restore(
            "msg-3",
            "turn-1",
            "tool-call-1",
            "echo_text",
            "ok",
            Instant.parse("2026-04-17T10:00:02Z")
        ));
        transcript.appendToolCall(ToolCall.builder()
            .toolCallId("tool-call-1")
            .toolName("echo_text")
            .argumentsJson("{\"text\":\"hello\"}")
            .turnId("turn-1")
            .build());
        transcript.appendToolResult(ToolResult.builder()
            .toolCallId("tool-call-1")
            .toolName("echo_text")
            .turnId("turn-1")
            .success(true)
            .output("hello")
            .build());

        TranscriptEntity entity = mapper.toEntity(transcript);
        assertTrue(entity.getMessagesJson().contains("\"turnId\":\"turn-1\""));

        ConversationTranscript restored = mapper.toModel(entity);
        assertEquals(3, restored.getMessages().size());
        assertEquals("turn-1", restored.getMessages().get(0).getTurnId());
        assertEquals("turn-1", restored.getMessages().get(1).getTurnId());
        assertEquals("turn-1", restored.getMessages().get(2).getTurnId());
        assertEquals("turn-1", restored.getToolCalls().get(0).getTurnId());
        assertEquals("turn-1", restored.getToolResults().get(0).getTurnId());
    }
}
