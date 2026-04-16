package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.id.ConversationIdGenerator;
import com.vi.agent.core.common.id.MessageIdGenerator;
import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.ToolCallIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.common.id.TurnIdGenerator;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.context.SimpleContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.port.TranscriptStore;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class RuntimeOrchestratorTest {

    @Test
    void executeShouldFinishSingleRoundWhenNoToolCall() {
        InMemoryStore transcriptStore = new InMemoryStore();

        AgentLoopEngine loopEngine = context -> new AssistantMessage("single-round-ok");
        ToolGateway toolGateway = new FakeToolGateway(List.of(), toolCall -> ToolResult.builder()
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .turnId(toolCall.getTurnId())
            .success(true)
            .output("done")
            .errorMessage("")
            .build());

        RuntimeOrchestrator orchestrator = buildOrchestrator(loopEngine, toolGateway, transcriptStore, 6);
        RuntimeExecutionResult result = orchestrator.execute("s-1", "hello");

        Assertions.assertEquals("single-round-ok", result.getAssistantMessage().getContent());
        Assertions.assertEquals(2, transcriptStore.saved.getMessages().size());
        Assertions.assertTrue(transcriptStore.saved.getMessages().get(0) instanceof UserMessage);
    }

    @Test
    void executeShouldRunToolAndFinishMultiRound() {
        InMemoryStore transcriptStore = new InMemoryStore();

        AgentLoopEngine loopEngine = context -> {
            if (context.getIteration() == 1) {
                ToolCall toolCall = ToolCall.builder()
                    .toolCallId("tc-1")
                    .toolName("echo_text")
                    .argumentsJson("{\"text\":\"hi\"}")
                    .turnId(context.getTurnId())
                    .build();
                return new AssistantMessage("need tool", List.of(toolCall));
            }
            return new AssistantMessage("final-answer");
        };

        ToolGateway toolGateway = new FakeToolGateway(
            List.of(ToolDefinition.builder().name("echo_text").description("echo").parametersJson("{}").build()),
            toolCall -> ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(true)
                .output("hi")
                .errorMessage("")
                .build()
        );

        RuntimeOrchestrator orchestrator = buildOrchestrator(loopEngine, toolGateway, transcriptStore, 6);
        RuntimeExecutionResult result = orchestrator.execute("s-2", "say hi");

        Assertions.assertEquals("final-answer", result.getAssistantMessage().getContent());
        Assertions.assertEquals(1, transcriptStore.saved.getToolCalls().size());
        Assertions.assertEquals(1, transcriptStore.saved.getToolResults().size());
    }

    @Test
    void executeShouldRecordToolFailureAndContinue() {
        InMemoryStore transcriptStore = new InMemoryStore();

        AgentLoopEngine loopEngine = context -> {
            if (context.getIteration() == 1) {
                ToolCall toolCall = ToolCall.builder()
                    .toolCallId("tc-fail")
                    .toolName("bad_tool")
                    .argumentsJson("{}")
                    .turnId(context.getTurnId())
                    .build();
                return new AssistantMessage("call tool", List.of(toolCall));
            }
            return new AssistantMessage("fallback-after-fail");
        };

        ToolGateway toolGateway = new FakeToolGateway(List.of(), toolCall -> {
            throw new AgentRuntimeException(ErrorCode.TOOL_EXECUTION_FAILED, "boom");
        });

        RuntimeOrchestrator orchestrator = buildOrchestrator(loopEngine, toolGateway, transcriptStore, 6);
        RuntimeExecutionResult result = orchestrator.execute("s-3", "trigger fail");

        Assertions.assertEquals("fallback-after-fail", result.getAssistantMessage().getContent());
        Assertions.assertEquals(1, transcriptStore.saved.getToolResults().size());
        Assertions.assertFalse(transcriptStore.saved.getToolResults().get(0).isSuccess());
    }

    @Test
    void executeShouldThrowWhenMaxIterationsExceeded() {
        InMemoryStore transcriptStore = new InMemoryStore();

        AgentLoopEngine loopEngine = context -> {
            ToolCall toolCall = ToolCall.builder()
                .toolCallId("tc-loop-" + context.getIteration())
                .toolName("echo_text")
                .argumentsJson("{}")
                .turnId(context.getTurnId())
                .build();
            return new AssistantMessage("still-loop", List.of(toolCall));
        };

        ToolGateway toolGateway = new FakeToolGateway(List.of(), toolCall -> ToolResult.builder()
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .turnId(toolCall.getTurnId())
            .success(true)
            .output("ok")
            .errorMessage("")
            .build());

        RuntimeOrchestrator orchestrator = buildOrchestrator(loopEngine, toolGateway, transcriptStore, 2);

        AgentRuntimeException exception = Assertions.assertThrows(AgentRuntimeException.class,
            () -> orchestrator.execute("s-4", "loop forever"));
        Assertions.assertEquals(ErrorCode.RUNTIME_MAX_ITERATIONS_EXCEEDED, exception.getErrorCode());
        Assertions.assertNotNull(transcriptStore.saved);
    }

    private RuntimeOrchestrator buildOrchestrator(
        AgentLoopEngine loopEngine,
        ToolGateway toolGateway,
        TranscriptStore transcriptStore,
        int maxIterations
    ) {
        return new RuntimeOrchestrator(
            new SimpleContextAssembler(),
            loopEngine,
            toolGateway,
            transcriptStore,
            new TraceIdGenerator(),
            new RunIdGenerator(),
            new ConversationIdGenerator(),
            new TurnIdGenerator(),
            new MessageIdGenerator(),
            new ToolCallIdGenerator(),
            maxIterations
        );
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

    private record FakeToolGateway(
        List<ToolDefinition> definitions,
        java.util.function.Function<ToolCall, ToolResult> executor
    ) implements ToolGateway {

        @Override
        public ToolResult route(ToolCall toolCall) {
            return executor.apply(toolCall);
        }

        @Override
        public List<ToolDefinition> listDefinitions() {
            return new ArrayList<>(definitions);
        }
    }
}
