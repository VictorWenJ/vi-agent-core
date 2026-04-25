package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.port.ConversationRepository;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.RunEventRepository;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunEventActorType;
import com.vi.agent.core.model.runtime.RunEventRecord;
import com.vi.agent.core.model.runtime.RunEventType;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.runtime.persistence.SessionWorkingSetLoader;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceCoordinatorFailureFlowTest {

    @Test
    void persistFailureShouldKeepSucceededFactsAndCancelPendingCalls() {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();
        StubRunEventRepository runEventRepository = new StubRunEventRepository();

        TestFieldUtils.setField(coordinator, "messageRepository", messageRepository);
        TestFieldUtils.setField(coordinator, "turnRepository", turnRepository);
        TestFieldUtils.setField(coordinator, "sessionRepository", sessionRepository);
        TestFieldUtils.setField(coordinator, "conversationRepository", conversationRepository);
        TestFieldUtils.setField(coordinator, "sessionWorkingSetRepository", sessionWorkingSetRepository);
        TestFieldUtils.setField(coordinator, "runEventRepository", runEventRepository);
        TestFieldUtils.setField(coordinator, "runIdentityFactory", new StubRunIdentityFactory());
        TestFieldUtils.setField(coordinator, "sessionWorkingSetLoader", new StubSessionWorkingSetLoader());

        AgentRunContext runContext = buildRunContext();
        AssistantMessage assistantDecision = AssistantMessage.create(
            "msg-assistant-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            2L,
            "call tools",
            List.of(
                toolCall("tcr-1", "call-1", ToolCallStatus.SUCCEEDED),
                toolCall("tcr-2", "call-2", ToolCallStatus.FAILED),
                toolCall("tcr-3", "call-3", ToolCallStatus.CREATED)
            ),
            FinishReason.TOOL_CALL,
            null
        );
        runContext.appendWorkingMessage(assistantDecision);
        runContext.appendToolCall(toolCall("tcr-1", "call-1", ToolCallStatus.SUCCEEDED));
        runContext.appendToolCall(toolCall("tcr-2", "call-2", ToolCallStatus.FAILED));
        runContext.appendToolCall(toolCall("tcr-3", "call-3", ToolCallStatus.CREATED));
        runContext.appendToolExecution(toolExecution("tex-1", "tcr-1", "call-1", ToolExecutionStatus.SUCCEEDED, "msg-tool-1"));
        runContext.appendToolExecution(toolExecution("tex-2", "tcr-2", "call-2", ToolExecutionStatus.FAILED, null));

        coordinator.persistFailure(runContext, "INVALID_MODEL_CONTEXT_MESSAGE", "invalid model context");

        assertEquals(1, messageRepository.savedAssistantMessages.size());
        assertEquals("msg-assistant-1", messageRepository.savedAssistantMessages.get(0).getMessageId());
        assertEquals(1, messageRepository.failureToolFactsCount);
        assertEquals(3, messageRepository.lastToolCalls.size());
        assertEquals(ToolCallStatus.SUCCEEDED, statusOf(messageRepository.lastToolCalls, "tcr-1"));
        assertEquals(ToolCallStatus.FAILED, statusOf(messageRepository.lastToolCalls, "tcr-2"));
        assertEquals(ToolCallStatus.CANCELLED, statusOf(messageRepository.lastToolCalls, "tcr-3"));
        assertEquals(2, messageRepository.lastToolExecutions.size());
        assertEquals(ToolExecutionStatus.SUCCEEDED, executionStatusOf(messageRepository.lastToolExecutions, "tcr-1"));
        assertEquals(ToolExecutionStatus.FAILED, executionStatusOf(messageRepository.lastToolExecutions, "tcr-2"));
        assertNull(findExecution(messageRepository.lastToolExecutions, "tcr-2").getToolResultMessageId());

        assertNotNull(turnRepository.lastUpdatedTurn);
        assertEquals(TurnStatus.FAILED, turnRepository.lastUpdatedTurn.getStatus());
        assertEquals("INVALID_MODEL_CONTEXT_MESSAGE", turnRepository.lastUpdatedTurn.getErrorCode());

        assertNotNull(sessionRepository.lastUpdatedSession);
        assertEquals(SessionStatus.ACTIVE, sessionRepository.lastUpdatedSession.getStatus());

        assertTrue(runEventRepository.savedEvents.stream().anyMatch(event -> event.getEventType() == RunEventType.TOOL_CANCELLED));
        assertTrue(runEventRepository.savedEvents.stream().anyMatch(event -> event.getEventType() == RunEventType.RUN_FAILED));
        assertTrue(runEventRepository.savedEvents.stream()
            .filter(event -> event.getEventType() == RunEventType.RUN_FAILED)
            .allMatch(event -> event.getActorType() == RunEventActorType.AGENT));
        assertEquals("sess-1", sessionWorkingSetRepository.lastEvictedSessionId);
    }

    @Test
    void toolLifecyclePersistenceShouldEmitEventsImmediately() {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();
        StubRunEventRepository runEventRepository = new StubRunEventRepository();

        TestFieldUtils.setField(coordinator, "messageRepository", messageRepository);
        TestFieldUtils.setField(coordinator, "turnRepository", turnRepository);
        TestFieldUtils.setField(coordinator, "sessionRepository", sessionRepository);
        TestFieldUtils.setField(coordinator, "conversationRepository", conversationRepository);
        TestFieldUtils.setField(coordinator, "sessionWorkingSetRepository", sessionWorkingSetRepository);
        TestFieldUtils.setField(coordinator, "runEventRepository", runEventRepository);
        TestFieldUtils.setField(coordinator, "runIdentityFactory", new StubRunIdentityFactory());
        TestFieldUtils.setField(coordinator, "sessionWorkingSetLoader", new StubSessionWorkingSetLoader());

        AgentRunContext runContext = buildRunContext();
        AssistantMessage assistantDecision = AssistantMessage.create(
            "msg-assistant-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            2L,
            "call tool",
            List.of(toolCall("tcr-1", "call-1", ToolCallStatus.CREATED)),
            FinishReason.TOOL_CALL,
            null
        );
        ToolExecution runningExecution = toolExecution("tex-1", "tcr-1", "call-1", ToolExecutionStatus.RUNNING, null);
        ToolExecution completedExecution = ToolExecution.builder()
            .toolExecutionId("tex-1")
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .toolResultMessageId("msg-tool-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("tool-1")
            .argumentsJson("{}")
            .outputText("{\"ok\":true}")
            .outputJson("{\"ok\":true}")
            .status(ToolExecutionStatus.SUCCEEDED)
            .durationMs(10L)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .createdAt(Instant.now())
            .build();
        ToolMessage toolMessage = ToolMessage.create(
            "msg-tool-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            3L,
            "{\"ok\":true}",
            "tcr-1",
            "call-1",
            "tool-1",
            ToolExecutionStatus.SUCCEEDED,
            null,
            null,
            10L,
            "{}"
        );

        coordinator.persistAssistantToolDecision(runContext, assistantDecision);
        coordinator.persistToolDispatched(runContext, toolCall("tcr-1", "call-1", ToolCallStatus.DISPATCHED));
        coordinator.persistToolStarted(runContext, toolCall("tcr-1", "call-1", ToolCallStatus.RUNNING), runningExecution);
        coordinator.persistToolCompleted(runContext, toolCall("tcr-1", "call-1", ToolCallStatus.SUCCEEDED), completedExecution, toolMessage);

        assertEquals(List.of(
            RunEventType.TOOL_CALL_CREATED,
            RunEventType.TOOL_DISPATCHED,
            RunEventType.TOOL_STARTED,
            RunEventType.TOOL_COMPLETED
        ), runEventRepository.savedEvents.stream().map(RunEventRecord::getEventType).toList());
        assertEquals(List.of(
            RunEventActorType.MODEL,
            RunEventActorType.TOOL,
            RunEventActorType.TOOL,
            RunEventActorType.TOOL
        ), runEventRepository.savedEvents.stream().map(RunEventRecord::getActorType).toList());
        assertEquals(List.of(ToolCallStatus.DISPATCHED, ToolCallStatus.RUNNING, ToolCallStatus.SUCCEEDED), messageRepository.updatedToolCallStatuses);
        assertEquals(1, messageRepository.runningExecutions.size());
        assertEquals(1, messageRepository.finalExecutions.size());
    }

    @Test
    void persistSuccessShouldWriteRunCompletedEvent() {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();
        StubRunEventRepository runEventRepository = new StubRunEventRepository();

        TestFieldUtils.setField(coordinator, "messageRepository", messageRepository);
        TestFieldUtils.setField(coordinator, "turnRepository", turnRepository);
        TestFieldUtils.setField(coordinator, "sessionRepository", sessionRepository);
        TestFieldUtils.setField(coordinator, "conversationRepository", conversationRepository);
        TestFieldUtils.setField(coordinator, "sessionWorkingSetRepository", sessionWorkingSetRepository);
        TestFieldUtils.setField(coordinator, "runEventRepository", runEventRepository);
        TestFieldUtils.setField(coordinator, "runIdentityFactory", new StubRunIdentityFactory());
        StubSessionWorkingSetLoader sessionWorkingSetLoader = new StubSessionWorkingSetLoader();
        TestFieldUtils.setField(coordinator, "sessionWorkingSetLoader", sessionWorkingSetLoader);

        AgentRunContext runContext = buildRunContext();
        AssistantMessage finalAssistant = AssistantMessage.create(
            "msg-assistant-final-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            3L,
            "done",
            List.of(),
            FinishReason.STOP,
            null
        );
        LoopExecutionResult loopExecutionResult = LoopExecutionResult.builder()
            .assistantMessage(finalAssistant)
            .appendedMessages(List.of(finalAssistant))
            .toolCalls(List.of())
            .toolExecutions(List.of())
            .finishReason(FinishReason.STOP)
            .usage(null)
            .build();

        coordinator.persistSuccess(runContext, loopExecutionResult);

        assertEquals(1, runEventRepository.savedEvents.size());
        assertEquals(RunEventType.RUN_COMPLETED, runEventRepository.savedEvents.get(0).getEventType());
        assertEquals(RunEventActorType.AGENT, runEventRepository.savedEvents.get(0).getActorType());
        assertEquals(1, sessionWorkingSetLoader.refreshCount);
        assertEquals(TurnStatus.COMPLETED, turnRepository.lastUpdatedTurn.getStatus());
    }

    @Test
    void persistSuccessShouldRefreshWorkingSetFromMysqlOnAfterCommit() {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();
        StubRunEventRepository runEventRepository = new StubRunEventRepository();
        StubSessionWorkingSetLoader sessionWorkingSetLoader = new StubSessionWorkingSetLoader();

        TestFieldUtils.setField(coordinator, "messageRepository", messageRepository);
        TestFieldUtils.setField(coordinator, "turnRepository", turnRepository);
        TestFieldUtils.setField(coordinator, "sessionRepository", sessionRepository);
        TestFieldUtils.setField(coordinator, "conversationRepository", conversationRepository);
        TestFieldUtils.setField(coordinator, "sessionWorkingSetRepository", sessionWorkingSetRepository);
        TestFieldUtils.setField(coordinator, "runEventRepository", runEventRepository);
        TestFieldUtils.setField(coordinator, "runIdentityFactory", new StubRunIdentityFactory());
        TestFieldUtils.setField(coordinator, "sessionWorkingSetLoader", sessionWorkingSetLoader);

        AgentRunContext runContext = buildRunContext();
        AssistantMessage finalAssistant = AssistantMessage.create(
            "msg-assistant-final-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            3L,
            "done",
            List.of(),
            FinishReason.STOP,
            null
        );
        LoopExecutionResult loopExecutionResult = LoopExecutionResult.builder()
            .assistantMessage(finalAssistant)
            .appendedMessages(List.of(finalAssistant))
            .toolCalls(List.of())
            .toolExecutions(List.of())
            .finishReason(FinishReason.STOP)
            .usage(null)
            .build();

        runContext.appendWorkingMessage(finalAssistant);
        coordinator.persistSuccess(runContext, loopExecutionResult);

        assertEquals("conv-1", sessionWorkingSetLoader.lastConversationId);
        assertEquals("sess-1", sessionWorkingSetLoader.lastSessionId);
        assertEquals(1, sessionWorkingSetLoader.refreshCount);
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
            .workingMessages(new ArrayList<>())
            .availableTools(List.of())
            .build();
    }

    private AssistantToolCall toolCall(String recordId, String callId, ToolCallStatus status) {
        return AssistantToolCall.builder()
            .toolCallRecordId(recordId)
            .toolCallId(callId)
            .assistantMessageId("msg-assistant-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("tool-" + callId)
            .argumentsJson("{}")
            .callIndex(0)
            .status(status)
            .createdAt(Instant.now())
            .build();
    }

    private ToolExecution toolExecution(
        String executionId,
        String recordId,
        String callId,
        ToolExecutionStatus status,
        String toolResultMessageId
    ) {
        return ToolExecution.builder()
            .toolExecutionId(executionId)
            .toolCallRecordId(recordId)
            .toolCallId(callId)
            .toolResultMessageId(toolResultMessageId)
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("tool-" + callId)
            .argumentsJson("{}")
            .status(status)
            .errorCode(status == ToolExecutionStatus.FAILED ? "TOOL_EXECUTION_FAILED" : null)
            .errorMessage(status == ToolExecutionStatus.FAILED ? "tool failed" : null)
            .durationMs(11L)
            .startedAt(Instant.now())
            .completedAt(status == ToolExecutionStatus.RUNNING ? null : Instant.now())
            .createdAt(Instant.now())
            .build();
    }

    private ToolCallStatus statusOf(List<AssistantToolCall> toolCalls, String toolCallRecordId) {
        return toolCalls.stream()
            .filter(toolCall -> toolCallRecordId.equals(toolCall.getToolCallRecordId()))
            .map(AssistantToolCall::getStatus)
            .findFirst()
            .orElse(null);
    }

    private ToolExecutionStatus executionStatusOf(List<ToolExecution> toolExecutions, String toolCallRecordId) {
        return toolExecutions.stream()
            .filter(execution -> toolCallRecordId.equals(execution.getToolCallRecordId()))
            .map(ToolExecution::getStatus)
            .findFirst()
            .orElse(null);
    }

    private ToolExecution findExecution(List<ToolExecution> toolExecutions, String toolCallRecordId) {
        return toolExecutions.stream()
            .filter(execution -> toolCallRecordId.equals(execution.getToolCallRecordId()))
            .findFirst()
            .orElse(null);
    }

    private static final class StubRunIdentityFactory extends RunIdentityFactory {
        private long index;

        @Override
        public String nextRunEventId() {
            return "evt-" + (++index);
        }
    }

    private static final class StubMessageRepository implements MessageRepository {
        private int failureToolFactsCount;
        private List<AssistantToolCall> lastToolCalls = List.of();
        private List<ToolExecution> lastToolExecutions = List.of();
        private final List<AssistantMessage> savedAssistantMessages = new ArrayList<>();
        private final List<ToolCallStatus> updatedToolCallStatuses = new ArrayList<>();
        private final List<ToolExecution> runningExecutions = new ArrayList<>();
        private final List<ToolExecution> finalExecutions = new ArrayList<>();

        @Override
        public void saveBatch(List<Message> messages) {
        }

        @Override
        public Message findByMessageId(String messageId) {
            return null;
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
        public Message findFinalAssistantMessageByTurnId(String turnId) {
            return null;
        }

        @Override
        public long nextSequenceNo(String sessionId) {
            return 1;
        }

        @Override
        public void saveFailureToolFacts(List<AssistantToolCall> toolCalls, List<ToolExecution> toolExecutions) {
            failureToolFactsCount++;
            lastToolCalls = toolCalls == null ? List.of() : toolCalls;
            lastToolExecutions = toolExecutions == null ? List.of() : toolExecutions;
        }

        @Override
        public void saveAssistantMessageIfAbsent(AssistantMessage assistantMessage) {
            savedAssistantMessages.add(assistantMessage);
        }

        @Override
        public void saveToolCallCreated(AssistantToolCall toolCall) {
        }

        @Override
        public void updateToolCallStatus(String toolCallRecordId, ToolCallStatus status) {
            updatedToolCallStatuses.add(status);
        }

        @Override
        public void upsertToolExecutionRunning(ToolExecution toolExecution) {
            runningExecutions.add(toolExecution);
        }

        @Override
        public void updateToolExecutionFinal(ToolExecution toolExecution) {
            finalExecutions.add(toolExecution);
        }
    }

    private static final class StubTurnRepository implements TurnRepository {
        private Turn lastUpdatedTurn;

        @Override
        public Turn findByRequestId(String requestId) {
            return null;
        }

        @Override
        public Turn findByTurnId(String turnId) {
            return null;
        }

        @Override
        public boolean existsRunningBySessionId(String sessionId) {
            return false;
        }

        @Override
        public void save(Turn turn) {
        }

        @Override
        public void update(Turn turn) {
            this.lastUpdatedTurn = turn;
        }
    }

    private static final class StubSessionRepository implements SessionRepository {
        private Session lastUpdatedSession;

        @Override
        public Optional<Session> findBySessionId(String sessionId) {
            return Optional.empty();
        }

        @Override
        public Optional<Session> findActiveByConversationId(String conversationId) {
            return Optional.empty();
        }

        @Override
        public void save(Session session) {
        }

        @Override
        public void update(Session session) {
            this.lastUpdatedSession = session;
        }
    }

    private static final class StubConversationRepository implements ConversationRepository {
        @Override
        public Optional<Conversation> findByConversationId(String conversationId) {
            return Optional.empty();
        }

        @Override
        public void save(Conversation conversation) {
        }

        @Override
        public void update(Conversation conversation) {
        }
    }

    private static final class StubSessionWorkingSetRepository implements SessionWorkingSetRepository {
        private String lastEvictedSessionId;

        @Override
        public SessionWorkingSetSnapshot findBySessionId(String sessionId) {
            return null;
        }

        @Override
        public void save(SessionWorkingSetSnapshot snapshot) {
        }

        @Override
        public void evict(String sessionId) {
            this.lastEvictedSessionId = sessionId;
        }
    }

    private static final class StubRunEventRepository implements RunEventRepository {
        private final List<RunEventRecord> savedEvents = new ArrayList<>();

        @Override
        public void saveBatch(List<RunEventRecord> runEvents) {
            savedEvents.addAll(runEvents);
        }
    }

    private static final class StubSessionWorkingSetLoader extends SessionWorkingSetLoader {
        private int refreshCount;
        private String lastConversationId;
        private String lastSessionId;

        @Override
        public SessionWorkingSetSnapshot refreshFromMysql(String conversationId, String sessionId) {
            refreshCount++;
            this.lastConversationId = conversationId;
            this.lastSessionId = sessionId;
            return SessionWorkingSetSnapshot.builder()
                .sessionId(sessionId)
                .conversationId(conversationId)
                .workingSetVersion(1L)
                .maxCompletedTurns(3)
                .summaryCoveredToSequenceNo(0L)
                .rawMessageId("msg-1")
                .updatedAt(Instant.now())
                .build();
        }
    }
}

