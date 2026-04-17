package com.vi.agent.core.infra.persistence.adapter;

import com.vi.agent.core.infra.persistence.mapper.RedisTranscriptMapper;
import com.vi.agent.core.infra.persistence.repository.InMemoryTranscriptRepository;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptStoreAdapterTest {

    @Test
    void saveAndLoadShouldKeepTurnId() {
        TranscriptStoreAdapter adapter = new TranscriptStoreAdapter(
            new InMemoryTranscriptRepository(),
            new RedisTranscriptMapper()
        );

        ConversationTranscript source = ConversationTranscript.start("session-1", "conversation-1");
        source.setTraceId("trace-1");
        source.setRunId("run-1");
        source.appendMessage(UserMessage.restore("msg-1", "turn-1", "hello", Instant.parse("2026-04-17T10:00:00Z")));
        source.appendMessage(AssistantMessage.restore("msg-2", "turn-1", "world", List.of(), Instant.parse("2026-04-17T10:00:01Z")));
        source.appendToolCall(ToolCall.builder()
            .toolCallId("tool-call-1")
            .toolName("echo_text")
            .argumentsJson("{\"text\":\"hello\"}")
            .turnId("turn-1")
            .build());
        source.appendToolResult(ToolResult.builder()
            .toolCallId("tool-call-1")
            .toolName("echo_text")
            .turnId("turn-1")
            .success(true)
            .output("hello")
            .build());

        adapter.save(source);
        ConversationTranscript loaded = adapter.load("session-1").orElseThrow();

        assertEquals(2, loaded.getMessages().size());
        assertEquals("turn-1", loaded.getMessages().get(0).getTurnId());
        assertEquals("turn-1", loaded.getMessages().get(1).getTurnId());
        assertEquals("turn-1", loaded.getToolCalls().get(0).getTurnId());
        assertEquals("turn-1", loaded.getToolResults().get(0).getTurnId());
        assertTrue(loaded.getMessages().stream().allMatch(message -> message.getTurnId() != null));
    }
}
