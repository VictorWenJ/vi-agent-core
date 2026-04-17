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
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolExecutionMessage;
import com.vi.agent.core.model.port.TranscriptStore;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.context.SimpleContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeOrchestratorTest {

    @Test
    void executeShouldCompleteInSingleRoundWithoutToolCall() {
        StubAgentLoopEngine loopEngine = new StubAgentLoopEngine(
            List.of(AssistantMessage.create("provider-msg-1", null, "single-round-answer", List.of())),
            List.of()
        );
        InMemoryTranscriptStore transcriptStore = new InMemoryTranscriptStore();
        StubToolGateway toolGateway = StubToolGateway.successGateway();
        RuntimeOrchestrator orchestrator = newOrchestrator(loopEngine, toolGateway, transcriptStore, 4);

        AgentExecutionResult result = orchestrator.execute("session-1", "hello");

        assertEquals(1, loopEngine.runCount);
        assertEquals(0, loopEngine.runStreamingCount);
        assertEquals("single-round-answer", result.getAssistantMessage().getContent());
        assertNotNull(result.getTurnId());
        assertEquals(result.getTurnId(), result.getAssistantMessage().getTurnId());

        ConversationTranscript saved = transcriptStore.lastSaved();
        assertNotNull(saved);
        assertEquals(2, saved.getMessages().size());
        assertEquals("user", saved.getMessages().get(0).getRole());
        assertEquals("assistant", saved.getMessages().get(1).getRole());
        assertEquals(result.getTurnId(), saved.getMessages().get(0).getTurnId());
        assertEquals(result.getTurnId(), saved.getMessages().get(1).getTurnId());
        assertTrue(saved.getToolCalls().isEmpty());
        assertTrue(saved.getToolResults().isEmpty());
    }

    @Test
    void executeShouldRunMultiRoundsWhenToolCallExists() {
        ToolCall providerToolCall = ToolCall.builder()
            .toolCallId("provider-call-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .build();

        StubAgentLoopEngine loopEngine = new StubAgentLoopEngine(
            List.of(
                AssistantMessage.create("provider-msg-1", null, "", List.of(providerToolCall)),
                AssistantMessage.create("provider-msg-2", null, "tool-final-answer", List.of())
            ),
            List.of()
        );
        InMemoryTranscriptStore transcriptStore = new InMemoryTranscriptStore();
        StubToolGateway toolGateway = new StubToolGateway(List.of(
            ToolDefinition.builder().name("get_time").description("mock").parametersJson("{\"type\":\"object\"}").build()
        ), call -> ToolResult.builder()
            .toolCallId(call.getToolCallId())
            .toolName(call.getToolName())
            .turnId(call.getTurnId())
            .success(true)
            .output("2026-04-17T10:00:00Z")
            .build());

        RuntimeOrchestrator orchestrator = newOrchestrator(loopEngine, toolGateway, transcriptStore, 4);

        AgentExecutionResult result = orchestrator.execute("session-2", "time?");

        assertEquals(2, loopEngine.runCount);
        assertEquals("tool-final-answer", result.getAssistantMessage().getContent());

        ConversationTranscript saved = transcriptStore.lastSaved();
        assertNotNull(saved);
        assertEquals(4, saved.getMessages().size());
        assertEquals(1, saved.getToolCalls().size());
        assertEquals(1, saved.getToolResults().size());
        Message toolExecution = saved.getMessages().stream()
            .filter(message -> message instanceof ToolExecutionMessage)
            .findFirst()
            .orElseThrow();
        assertEquals(result.getTurnId(), toolExecution.getTurnId());
        assertEquals(result.getTurnId(), saved.getToolCalls().get(0).getTurnId());
        assertEquals(result.getTurnId(), saved.getToolResults().get(0).getTurnId());
    }

    @Test
    void executeStreamingShouldEmitTokenAndCompleteEvents() {
        StubAgentLoopEngine loopEngine = new StubAgentLoopEngine(
            List.of(),
            List.of(new StreamingResponse(
                List.of("Hel", "lo"),
                AssistantMessage.create("provider-msg-stream", null, "Hello", List.of())
            ))
        );
        InMemoryTranscriptStore transcriptStore = new InMemoryTranscriptStore();
        StubToolGateway toolGateway = StubToolGateway.successGateway();
        RuntimeOrchestrator orchestrator = newOrchestrator(loopEngine, toolGateway, transcriptStore, 4);

        List<RuntimeEvent> events = new ArrayList<>();
        AgentExecutionResult result = orchestrator.executeStreaming("session-stream", "hi", events::add);

        assertEquals(0, loopEngine.runCount);
        assertEquals(1, loopEngine.runStreamingCount);
        assertEquals("Hello", result.getAssistantMessage().getContent());

        List<RuntimeEvent> tokenEvents = events.stream()
            .filter(event -> event.getType() == RuntimeEventType.TOKEN)
            .toList();
        assertEquals(2, tokenEvents.size());
        assertEquals("Hel", tokenEvents.get(0).getContent());
        assertEquals("lo", tokenEvents.get(1).getContent());

        RuntimeEvent completeEvent = events.stream()
            .filter(event -> event.getType() == RuntimeEventType.COMPLETE)
            .findFirst()
            .orElseThrow();
        assertTrue(completeEvent.isDone());
        assertEquals("Hello", completeEvent.getContent());
    }

    @Test
    void executeShouldThrowWhenMaxIterationsExceeded() {
        ToolCall providerToolCall = ToolCall.builder()
            .toolCallId("provider-call-max")
            .toolName("echo_text")
            .argumentsJson("{\"text\":\"x\"}")
            .build();
        StubAgentLoopEngine loopEngine = new StubAgentLoopEngine(
            List.of(AssistantMessage.create("provider-msg-max", null, "", List.of(providerToolCall))),
            List.of()
        );
        InMemoryTranscriptStore transcriptStore = new InMemoryTranscriptStore();
        StubToolGateway toolGateway = new StubToolGateway(List.of(
            ToolDefinition.builder().name("echo_text").description("mock").parametersJson("{}").build()
        ), call -> ToolResult.builder()
            .toolCallId(call.getToolCallId())
            .toolName(call.getToolName())
            .turnId(call.getTurnId())
            .success(true)
            .output("ok")
            .build());
        RuntimeOrchestrator orchestrator = newOrchestrator(loopEngine, toolGateway, transcriptStore, 1);

        AgentRuntimeException exception = assertThrows(
            AgentRuntimeException.class,
            () -> orchestrator.execute("session-max", "loop forever")
        );
        assertEquals(ErrorCode.RUNTIME_MAX_ITERATIONS_EXCEEDED, exception.getErrorCode());
    }

    private RuntimeOrchestrator newOrchestrator(
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

    private static final class StubAgentLoopEngine implements AgentLoopEngine {

        private final Deque<AssistantMessage> syncResponses = new ArrayDeque<>();
        private final Deque<StreamingResponse> streamingResponses = new ArrayDeque<>();

        private int runCount;
        private int runStreamingCount;

        private StubAgentLoopEngine(List<AssistantMessage> syncResponses, List<StreamingResponse> streamingResponses) {
            this.syncResponses.addAll(syncResponses);
            this.streamingResponses.addAll(streamingResponses);
        }

        @Override
        public AssistantMessage run(AgentRunContext runContext) {
            runCount++;
            AssistantMessage message = syncResponses.pollFirst();
            if (message == null) {
                throw new IllegalStateException("No sync response prepared");
            }
            return message;
        }

        @Override
        public AssistantMessage runStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer) {
            runStreamingCount++;
            StreamingResponse response = streamingResponses.pollFirst();
            if (response == null) {
                throw new IllegalStateException("No streaming response prepared");
            }
            for (String chunk : response.chunks()) {
                chunkConsumer.accept(chunk);
            }
            return response.assistantMessage();
        }
    }

    private record StreamingResponse(List<String> chunks, AssistantMessage assistantMessage) {
    }

    private static final class StubToolGateway implements ToolGateway {

        private final List<ToolDefinition> definitions;
        private final Function<ToolCall, ToolResult> routeFunction;

        private StubToolGateway(List<ToolDefinition> definitions, Function<ToolCall, ToolResult> routeFunction) {
            this.definitions = definitions;
            this.routeFunction = routeFunction;
        }

        private static StubToolGateway successGateway() {
            return new StubToolGateway(List.of(), toolCall -> ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(true)
                .output("")
                .build());
        }

        @Override
        public ToolResult route(ToolCall toolCall) {
            return routeFunction.apply(toolCall);
        }

        @Override
        public List<ToolDefinition> listDefinitions() {
            return definitions;
        }
    }

    private static final class InMemoryTranscriptStore implements TranscriptStore {

        private final Map<String, ConversationTranscript> storage = new HashMap<>();
        private ConversationTranscript lastSaved;

        @Override
        public Optional<ConversationTranscript> load(String sessionId) {
            return Optional.ofNullable(storage.get(sessionId));
        }

        @Override
        public void save(ConversationTranscript transcript) {
            storage.put(transcript.getSessionId(), transcript);
            lastSaved = transcript;
        }

        private ConversationTranscript lastSaved() {
            return lastSaved;
        }
    }
}
