package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.context.SimpleContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.port.TranscriptStore;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class RuntimeOrchestratorTest {

    @Test
    void executeShouldPersistMinimalTranscript() {
        InMemoryStore transcriptStore = new InMemoryStore();

        AgentLoopEngine loopEngine = context -> new AssistantMessage("ok");
        ToolGateway toolGateway = toolCall -> new ToolResult(toolCall.getToolCallId(), toolCall.getToolName(), true, "done");

        RuntimeOrchestrator orchestrator = new RuntimeOrchestrator(
            new SimpleContextAssembler(),
            loopEngine,
            toolGateway,
            transcriptStore,
            new TraceIdGenerator(),
            new RunIdGenerator()
        );

        RuntimeExecutionResult result = orchestrator.execute("s-1", "hello");

        Assertions.assertNotNull(result.getTraceId());
        Assertions.assertNotNull(result.getRunId());

        ConversationTranscript transcript = transcriptStore.saved;
        Assertions.assertNotNull(transcript);
        Assertions.assertEquals("s-1", transcript.getSessionId());
        Assertions.assertEquals(2, transcript.getMessages().size());
        Assertions.assertTrue(transcript.getMessages().get(0) instanceof UserMessage);
    }

    @Test
    void executeShouldRouteToolCallsThroughToolGateway() {
        InMemoryStore transcriptStore = new InMemoryStore();

        AgentLoopEngine loopEngine = context -> new AssistantMessage(
            "need tool",
            java.util.List.of(new ToolCall("tc-1", "get_time", "{}"))
        );
        ToolGateway toolGateway = toolCall -> new ToolResult(toolCall.getToolCallId(), toolCall.getToolName(), true, "2026-04-15T00:00:00");

        RuntimeOrchestrator orchestrator = new RuntimeOrchestrator(
            new SimpleContextAssembler(),
            loopEngine,
            toolGateway,
            transcriptStore,
            new TraceIdGenerator(),
            new RunIdGenerator()
        );

        orchestrator.execute("s-2", "time");

        Assertions.assertEquals(1, transcriptStore.saved.getToolResults().size());
    }

    private static class InMemoryStore implements TranscriptStore {
        private ConversationTranscript saved;

        @Override
        public Optional<ConversationTranscript> load(String sessionId) {
            return Optional.empty();
        }

        @Override
        public void save(ConversationTranscript transcript) {
            this.saved = transcript;
        }
    }
}
