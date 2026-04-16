package com.vi.agent.core.infra.persistence;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class TranscriptStoreServiceTest {

    @Test
    void saveAndLoadShouldRestoreTranscript() {
        TranscriptRepository repository = new InMemoryTranscriptRepository();
        TranscriptRedisMapper mapper = new TranscriptRedisMapper();
        TranscriptStoreService storeService = new TranscriptStoreService(repository, mapper);

        ConversationTranscript transcript = new ConversationTranscript("session-1", "conv-1");
        transcript.setTraceId("trace-1");
        transcript.setRunId("run-1");
        transcript.appendMessage(new UserMessage("msg-1", "hello"));
        transcript.appendMessage(new AssistantMessage("msg-2", "hi", java.util.List.of()));
        transcript.appendToolCall(ToolCall.builder()
            .toolCallId("tc-1")
            .toolName("echo_text")
            .argumentsJson("{}")
            .turnId("turn-1")
            .build());
        transcript.appendToolResult(ToolResult.builder()
            .toolCallId("tc-1")
            .toolName("echo_text")
            .turnId("turn-1")
            .success(true)
            .output("ok")
            .errorMessage("")
            .build());

        storeService.save(transcript);
        Optional<ConversationTranscript> loaded = storeService.load("session-1");

        Assertions.assertTrue(loaded.isPresent());
        Assertions.assertEquals("conv-1", loaded.get().getConversationId());
        Assertions.assertEquals(2, loaded.get().getMessages().size());
        Assertions.assertEquals(1, loaded.get().getToolCalls().size());
        Assertions.assertEquals(1, loaded.get().getToolResults().size());
    }
}
