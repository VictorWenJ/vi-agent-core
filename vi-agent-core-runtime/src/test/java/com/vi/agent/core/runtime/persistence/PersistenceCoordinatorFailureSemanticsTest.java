package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistenceCoordinatorFailureSemanticsTest {

    @Test
    void failureShouldMarkTurnFailedButKeepSessionActive() throws Exception {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        CapturingTurnRepository turnRepository = new CapturingTurnRepository();
        CapturingSessionRepository sessionRepository = new CapturingSessionRepository();
        CapturingSessionStateRepository sessionStateRepository = new CapturingSessionStateRepository();

        setField(coordinator, "turnRepository", turnRepository);
        setField(coordinator, "sessionRepository", sessionRepository);
        setField(coordinator, "sessionStateRepository", sessionStateRepository);

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
            .userMessageId("user-1")
            .createdAt(Instant.now())
            .build();

        AgentRunContext runContext = AgentRunContext.builder()
            .runMetadata(RunMetadata.builder().traceId("trace-1").runId("run-1").turnId("turn-1").build())
            .conversation(Conversation.builder()
                .conversationId("conv-1")
                .status(ConversationStatus.ACTIVE)
                .activeSessionId("sess-1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build())
            .session(session)
            .turn(turn)
            .userInput("hello")
            .workingMessages(List.of())
            .availableTools(List.of())
            .build();

        coordinator.persistFailure(runContext, "E-1", "boom");

        assertEquals(TurnStatus.FAILED, turn.getStatus());
        assertEquals("E-1", turn.getErrorCode());
        assertEquals("boom", turn.getErrorMessage());
        assertEquals(TurnStatus.FAILED, turnRepository.updatedTurn.getStatus());

        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertEquals(SessionStatus.ACTIVE, sessionRepository.updatedSession.getStatus());
    }

    @Test
    void failureShouldEvictSessionStateCache() throws Exception {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        CapturingTurnRepository turnRepository = new CapturingTurnRepository();
        CapturingSessionRepository sessionRepository = new CapturingSessionRepository();
        CapturingSessionStateRepository sessionStateRepository = new CapturingSessionStateRepository();

        setField(coordinator, "turnRepository", turnRepository);
        setField(coordinator, "sessionRepository", sessionRepository);
        setField(coordinator, "sessionStateRepository", sessionStateRepository);

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
            .userMessageId("user-1")
            .createdAt(Instant.now())
            .build();

        AgentRunContext runContext = AgentRunContext.builder()
            .runMetadata(RunMetadata.builder().traceId("trace-1").runId("run-1").turnId("turn-1").build())
            .conversation(Conversation.builder()
                .conversationId("conv-1")
                .status(ConversationStatus.ACTIVE)
                .activeSessionId("sess-1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build())
            .session(session)
            .turn(turn)
            .userInput("hello")
            .workingMessages(List.of())
            .availableTools(List.of())
            .build();

        coordinator.persistFailure(runContext, "E-1", "boom");

        assertEquals("sess-1", sessionStateRepository.evictedSessionId);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = PersistenceCoordinator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CapturingTurnRepository implements TurnRepository {
        private Turn updatedTurn;

        @Override
        public Optional<Turn> findByRequestId(String requestId) {
            return Optional.empty();
        }

        @Override
        public Optional<Turn> findByTurnId(String turnId) {
            return Optional.empty();
        }

        @Override
        public boolean existsRunningBySessionId(String sessionId) {
            return false;
        }

        @Override
        public void save(Turn turn) {
            // no-op
        }

        @Override
        public void update(Turn turn) {
            this.updatedTurn = turn;
        }
    }

    private static final class CapturingSessionRepository implements SessionRepository {
        private Session updatedSession;

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
            // no-op
        }

        @Override
        public void update(Session session) {
            this.updatedSession = session;
        }
    }

    private static final class CapturingSessionStateRepository implements SessionStateRepository {
        private String evictedSessionId;

        @Override
        public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
            return Optional.empty();
        }

        @Override
        public void save(SessionStateSnapshot snapshot) {
            // no-op
        }

        @Override
        public void evict(String sessionId) {
            this.evictedSessionId = sessionId;
        }
    }
}

