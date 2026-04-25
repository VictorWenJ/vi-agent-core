package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleAgentLoopEngineTest {

    @Test
    void toolFailureShouldNotCreateToolMessageAndShouldRecordFailedExecution() {
        SimpleAgentLoopEngine engine = new SimpleAgentLoopEngine();
        MessageFactory messageFactory = createMessageFactory();
        RecordingPersistenceCoordinator persistenceCoordinator = new RecordingPersistenceCoordinator();
        StubLlmGateway llmGateway = new StubLlmGateway(List.of(ModelResponse.builder()
            .content("call tool")
            .finishReason(FinishReason.TOOL_CALL)
            .toolCalls(List.of(ModelToolCall.builder()
                .toolCallId("call-1")
                .toolName("tool-a")
                .argumentsJson("{\"k\":\"v\"}")
                .build()))
            .build()));
        StubToolGateway toolGateway = new StubToolGateway(List.of(ToolResult.builder()
            .toolCallRecordId("ignored")
            .toolCallId("call-1")
            .toolName("tool-a")
            .turnId("turn-1")
            .success(false)
            .output("")
            .errorCode("TOOL_EXECUTION_FAILED")
            .errorMessage("tool failed")
            .durationMs(12L)
            .build()));

        TestFieldUtils.setField(engine, "llmGateway", llmGateway);
        TestFieldUtils.setField(engine, "toolGateway", toolGateway);
        TestFieldUtils.setField(engine, "messageFactory", messageFactory);
        TestFieldUtils.setField(engine, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(engine, "maxIterations", 3);

        AgentRunContext runContext = buildRunContext();

        assertThrows(AgentRuntimeException.class, () -> engine.run(runContext));
        assertEquals(2, runContext.getWorkingMessages().size());
        assertFalse(runContext.getWorkingMessages().stream().anyMatch(message -> message.getRole() == MessageRole.TOOL));
        assertEquals(1, runContext.getToolCalls().size());
        assertEquals(1, runContext.getToolExecutions().size());
        ToolExecution toolExecution = runContext.getToolExecutions().get(0);
        assertEquals(ToolExecutionStatus.FAILED, toolExecution.getStatus());
        assertEquals("TOOL_EXECUTION_FAILED", toolExecution.getErrorCode());
        assertEquals(List.of(
            ToolCallStatus.CREATED,
            ToolCallStatus.DISPATCHED,
            ToolCallStatus.RUNNING,
            ToolCallStatus.FAILED
        ), persistenceCoordinator.toolCallStatusTransitions);
        assertEquals(List.of(ToolExecutionStatus.RUNNING, ToolExecutionStatus.FAILED), persistenceCoordinator.toolExecutionStatusTransitions);
        assertEquals(1, toolGateway.executedCount());
    }

    @Test
    void toolSuccessShouldStillCreateToolMessage() {
        SimpleAgentLoopEngine engine = new SimpleAgentLoopEngine();
        MessageFactory messageFactory = createMessageFactory();
        RecordingPersistenceCoordinator persistenceCoordinator = new RecordingPersistenceCoordinator();
        StubLlmGateway llmGateway = new StubLlmGateway(List.of(
            ModelResponse.builder()
                .content("call tool")
                .finishReason(FinishReason.TOOL_CALL)
                .toolCalls(List.of(ModelToolCall.builder()
                    .toolCallId("call-1")
                    .toolName("tool-a")
                    .argumentsJson("{\"k\":\"v\"}")
                    .build()))
                .build(),
            ModelResponse.builder()
                .content("done")
                .finishReason(FinishReason.STOP)
                .toolCalls(List.of())
                .build()
        ));
        StubToolGateway toolGateway = new StubToolGateway(List.of(ToolResult.builder()
            .toolCallRecordId("ignored")
            .toolCallId("call-1")
            .toolName("tool-a")
            .turnId("turn-1")
            .success(true)
            .output("{\"ok\":true}")
            .durationMs(8L)
            .build()));

        TestFieldUtils.setField(engine, "llmGateway", llmGateway);
        TestFieldUtils.setField(engine, "toolGateway", toolGateway);
        TestFieldUtils.setField(engine, "messageFactory", messageFactory);
        TestFieldUtils.setField(engine, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(engine, "maxIterations", 3);

        AgentRunContext runContext = buildRunContext();
        LoopExecutionResult loopExecutionResult = engine.run(runContext);

        assertTrue(loopExecutionResult.getAppendedMessages().stream().anyMatch(message -> message.getRole() == MessageRole.TOOL));
        assertTrue(runContext.getWorkingMessages().stream().anyMatch(message -> message.getRole() == MessageRole.TOOL));
        assertEquals(List.of(
            ToolCallStatus.CREATED,
            ToolCallStatus.DISPATCHED,
            ToolCallStatus.RUNNING,
            ToolCallStatus.SUCCEEDED
        ), persistenceCoordinator.toolCallStatusTransitions);
        assertEquals(List.of(ToolExecutionStatus.RUNNING, ToolExecutionStatus.SUCCEEDED), persistenceCoordinator.toolExecutionStatusTransitions);
        assertEquals(1, toolGateway.executedCount());
    }

    @Test
    void multiToolFailureShouldNotExecuteRemainingToolCalls() {
        SimpleAgentLoopEngine engine = new SimpleAgentLoopEngine();
        MessageFactory messageFactory = createMessageFactory();
        RecordingPersistenceCoordinator persistenceCoordinator = new RecordingPersistenceCoordinator();
        StubLlmGateway llmGateway = new StubLlmGateway(List.of(ModelResponse.builder()
            .content("call tools")
            .finishReason(FinishReason.TOOL_CALL)
            .toolCalls(List.of(
                ModelToolCall.builder().toolCallId("call-1").toolName("tool-a").argumentsJson("{\"id\":1}").build(),
                ModelToolCall.builder().toolCallId("call-2").toolName("tool-b").argumentsJson("{\"id\":2}").build(),
                ModelToolCall.builder().toolCallId("call-3").toolName("tool-c").argumentsJson("{\"id\":3}").build()
            ))
            .build()));
        StubToolGateway toolGateway = new StubToolGateway(List.of(
            ToolResult.builder()
                .toolCallRecordId("ignored")
                .toolCallId("call-1")
                .toolName("tool-a")
                .turnId("turn-1")
                .success(true)
                .output("{\"ok\":1}")
                .durationMs(5L)
                .build(),
            ToolResult.builder()
                .toolCallRecordId("ignored")
                .toolCallId("call-2")
                .toolName("tool-b")
                .turnId("turn-1")
                .success(false)
                .output("")
                .errorCode("TOOL_EXECUTION_FAILED")
                .errorMessage("tool-b failed")
                .durationMs(6L)
                .build()
        ));

        TestFieldUtils.setField(engine, "llmGateway", llmGateway);
        TestFieldUtils.setField(engine, "toolGateway", toolGateway);
        TestFieldUtils.setField(engine, "messageFactory", messageFactory);
        TestFieldUtils.setField(engine, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(engine, "maxIterations", 3);

        AgentRunContext runContext = buildRunContext();

        assertThrows(AgentRuntimeException.class, () -> engine.run(runContext));
        assertEquals(2, toolGateway.executedCount());
        assertEquals(1, persistenceCoordinator.toolCompletedCount);
        assertEquals(1, persistenceCoordinator.toolFailedCount);
        assertEquals(0, persistenceCoordinator.toolCancelledCount);
        assertEquals(List.of(
            ToolCallStatus.CREATED,
            ToolCallStatus.CREATED,
            ToolCallStatus.CREATED,
            ToolCallStatus.DISPATCHED,
            ToolCallStatus.RUNNING,
            ToolCallStatus.SUCCEEDED,
            ToolCallStatus.DISPATCHED,
            ToolCallStatus.RUNNING,
            ToolCallStatus.FAILED
        ), persistenceCoordinator.toolCallStatusTransitions);
    }

    private MessageFactory createMessageFactory() {
        MessageFactory messageFactory = new MessageFactory();
        TestFieldUtils.setField(messageFactory, "messageRepository", new InMemoryMessageRepository());
        TestFieldUtils.setField(messageFactory, "runIdentityFactory", new StubRunIdentityFactory());
        return messageFactory;
    }

    private AgentRunContext buildRunContext() {
        Conversation conversation = Conversation.builder()
            .conversationId("conv-1")
            .status(ConversationStatus.ACTIVE)
            .activeSessionId("sess-1")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .lastMessageAt(Instant.now())
            .build();
        Session session = Session.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .status(SessionStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        Turn turn = Turn.builder()
            .turnId("turn-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .requestId("req-1")
            .runId("run-1")
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-user-1")
            .createdAt(Instant.now())
            .build();
        return AgentRunContext.builder()
            .runMetadata(RunMetadata.builder().traceId("trace-1").runId("run-1").turnId("turn-1").build())
            .conversation(conversation)
            .session(session)
            .turn(turn)
            .userInput("hello")
            .workingMessages(new ArrayList<>(List.of(UserMessage.create(
                "msg-user-1",
                "conv-1",
                "sess-1",
                "turn-1",
                "run-1",
                1L,
                "hello"
            ))))
            .availableTools(List.of())
            .build();
    }

    private static final class StubLlmGateway implements LlmGateway {
        private final Queue<ModelResponse> responses;

        private StubLlmGateway(List<ModelResponse> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public ModelResponse generate(ModelRequest modelRequest) {
            return responses.remove();
        }

        @Override
        public ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer) {
            return generate(modelRequest);
        }
    }

    private static final class StubToolGateway implements ToolGateway {
        private final Queue<ToolResult> toolResults;
        private final AtomicInteger executedCount = new AtomicInteger();

        private StubToolGateway(List<ToolResult> toolResults) {
            this.toolResults = new ArrayDeque<>(toolResults);
        }

        @Override
        public ToolResult execute(ToolCall toolCall) {
            executedCount.incrementAndGet();
            return toolResults.remove();
        }

        @Override
        public List<ToolDefinition> listDefinitions() {
            return List.of();
        }

        private int executedCount() {
            return executedCount.get();
        }
    }

    private static final class InMemoryMessageRepository implements MessageRepository {
        @Override
        public void saveBatch(List<Message> messages) {
        }

        @Override
        public Optional<Message> findByMessageId(String messageId) {
            return Optional.empty();
        }

        @Override
        public List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns) {
            return List.of();
        }

        @Override
        public List<Message> findByTurnId(String turnId) {
            return List.of();
        }

        @Override
        public Optional<Message> findFinalAssistantMessageByTurnId(String turnId) {
            return Optional.empty();
        }

        @Override
        public long nextSequenceNo(String sessionId) {
            return 1L;
        }
    }

    private static final class StubRunIdentityFactory extends RunIdentityFactory {
        private long messageIndex;
        private long toolCallRecordIndex;
        private long toolExecutionIndex;

        @Override
        public String nextMessageId() {
            return "msg-" + (++messageIndex);
        }

        @Override
        public String nextToolCallRecordId() {
            return "tcr-" + (++toolCallRecordIndex);
        }

        @Override
        public String nextToolExecutionId() {
            return "tex-" + (++toolExecutionIndex);
        }
    }

    private static final class RecordingPersistenceCoordinator extends PersistenceCoordinator {
        private final List<ToolCallStatus> toolCallStatusTransitions = new ArrayList<>();
        private final List<ToolExecutionStatus> toolExecutionStatusTransitions = new ArrayList<>();
        private int toolCompletedCount;
        private int toolFailedCount;
        private int toolCancelledCount;

        @Override
        public void persistAssistantToolDecision(AgentRunContext runContext, AssistantMessage assistantMessage) {
            if (assistantMessage == null || assistantMessage.getToolCalls() == null) {
                return;
            }
            assistantMessage.getToolCalls().forEach(toolCall -> toolCallStatusTransitions.add(ToolCallStatus.CREATED));
        }

        @Override
        public void persistToolDispatched(AgentRunContext runContext, AssistantToolCall toolCall) {
            toolCallStatusTransitions.add(ToolCallStatus.DISPATCHED);
        }

        @Override
        public void persistToolStarted(AgentRunContext runContext, AssistantToolCall toolCall, ToolExecution runningExecution) {
            toolCallStatusTransitions.add(ToolCallStatus.RUNNING);
            toolExecutionStatusTransitions.add(ToolExecutionStatus.RUNNING);
        }

        @Override
        public void persistToolCompleted(AgentRunContext runContext, AssistantToolCall toolCall, ToolExecution completedExecution, Message toolMessage) {
            toolCallStatusTransitions.add(ToolCallStatus.SUCCEEDED);
            toolExecutionStatusTransitions.add(ToolExecutionStatus.SUCCEEDED);
            toolCompletedCount++;
        }

        @Override
        public void persistToolFailed(AgentRunContext runContext, AssistantToolCall toolCall, ToolExecution failedExecution) {
            toolCallStatusTransitions.add(ToolCallStatus.FAILED);
            toolExecutionStatusTransitions.add(ToolExecutionStatus.FAILED);
            toolFailedCount++;
        }

        @Override
        public void persistToolCancelled(AgentRunContext runContext, AssistantToolCall cancelledToolCall) {
            toolCallStatusTransitions.add(ToolCallStatus.CANCELLED);
            toolCancelledCount++;
        }
    }
}
