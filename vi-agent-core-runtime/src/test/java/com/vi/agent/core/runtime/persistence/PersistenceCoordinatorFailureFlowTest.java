package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.ConversationRepository;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.RunEventRepository;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.RunEventRecord;
import com.vi.agent.core.model.runtime.RunEventType;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.session.SessionStatus;
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

class PersistenceCoordinatorFailureFlowTest {

    @Test
    void persistFailureShouldPersistToolFactsAndKeepSessionActive() {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionStateRepository sessionStateRepository = new StubSessionStateRepository();
        StubRunEventRepository runEventRepository = new StubRunEventRepository();

        TestFieldUtils.setField(coordinator, "messageRepository", messageRepository);
        TestFieldUtils.setField(coordinator, "turnRepository", turnRepository);
        TestFieldUtils.setField(coordinator, "sessionRepository", sessionRepository);
        TestFieldUtils.setField(coordinator, "conversationRepository", conversationRepository);
        TestFieldUtils.setField(coordinator, "sessionStateRepository", sessionStateRepository);
        TestFieldUtils.setField(coordinator, "runEventRepository", runEventRepository);
        TestFieldUtils.setField(coordinator, "runIdentityFactory", new StubRunIdentityFactory());

        AgentRunContext runContext = buildRunContext();
        runContext.appendToolCall(AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("tool-a")
            .argumentsJson("{\"k\":\"v\"}")
            .callIndex(0)
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build());
        runContext.appendToolExecution(ToolExecution.builder()
            .toolExecutionId("tex-1")
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .toolResultMessageId("tex-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("tool-a")
            .argumentsJson("{\"k\":\"v\"}")
            .status(ToolExecutionStatus.FAILED)
            .errorCode("TOOL_EXECUTION_FAILED")
            .errorMessage("tool failed")
            .durationMs(13L)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .createdAt(Instant.now())
            .build());

        coordinator.persistFailure(runContext, "INVALID_MODEL_CONTEXT_MESSAGE", "invalid model context");

        assertEquals(1, messageRepository.failureToolFactsCount);
        assertEquals(1, messageRepository.lastToolCalls.size());
        assertEquals(1, messageRepository.lastToolExecutions.size());

        assertNotNull(turnRepository.lastUpdatedTurn);
        assertEquals(TurnStatus.FAILED, turnRepository.lastUpdatedTurn.getStatus());
        assertEquals("INVALID_MODEL_CONTEXT_MESSAGE", turnRepository.lastUpdatedTurn.getErrorCode());

        assertNotNull(sessionRepository.lastUpdatedSession);
        assertEquals(SessionStatus.ACTIVE, sessionRepository.lastUpdatedSession.getStatus());

        assertEquals(1, runEventRepository.savedEvents.size());
        assertEquals(RunEventType.RUN_FAILED, runEventRepository.savedEvents.get(0).getEventType());

        assertEquals("sess-1", sessionStateRepository.lastEvictedSessionId);
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

    private static final class StubRunIdentityFactory extends RunIdentityFactory {
        @Override
        public String nextRunEventId() {
            return "evt-1";
        }
    }

    private static final class StubMessageRepository implements MessageRepository {
        private int failureToolFactsCount;
        private List<AssistantToolCall> lastToolCalls = List.of();
        private List<ToolExecution> lastToolExecutions = List.of();

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

    private static final class StubSessionStateRepository implements SessionStateRepository {
        private String lastEvictedSessionId;

        @Override
        public SessionStateSnapshot findBySessionId(String sessionId) {
            return null;
        }

        @Override
        public void save(SessionStateSnapshot snapshot) {
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
}
