package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionMode;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TurnLifecycleServiceTest {

    @Test
    void startTurnShouldCreateUserMessageCreateRunningTurnAndPersistUserMessage() {
        TurnLifecycleService service = new TurnLifecycleService();
        StubMessageFactory messageFactory = new StubMessageFactory();
        StubTurnRepository turnRepository = new StubTurnRepository();
        StubPersistenceCoordinator persistenceCoordinator = new StubPersistenceCoordinator();

        TestFieldUtils.setField(service, "messageFactory", messageFactory);
        TestFieldUtils.setField(service, "turnRepository", turnRepository);
        TestFieldUtils.setField(service, "persistenceCoordinator", persistenceCoordinator);

        RuntimeExecutionContext context = buildContext();
        TurnStartResult turnStartResult = service.startTurn(context);

        assertNotNull(turnStartResult);
        assertSame(messageFactory.userMessageToReturn, turnStartResult.getUserMessage());
        assertSame(turnRepository.savedTurn, turnStartResult.getTurn());

        assertEquals("conv-1", messageFactory.lastConversationId);
        assertEquals("sess-1", messageFactory.lastSessionId);
        assertEquals("turn-1", messageFactory.lastTurnId);
        assertEquals("run-1", messageFactory.lastRunId);
        assertEquals("hello", messageFactory.lastContent);

        assertNotNull(turnRepository.savedTurn);
        assertEquals("turn-1", turnRepository.savedTurn.getTurnId());
        assertEquals("conv-1", turnRepository.savedTurn.getConversationId());
        assertEquals("sess-1", turnRepository.savedTurn.getSessionId());
        assertEquals("req-1", turnRepository.savedTurn.getRequestId());
        assertEquals("run-1", turnRepository.savedTurn.getRunId());
        assertEquals("msg-user-1", turnRepository.savedTurn.getUserMessageId());
        assertEquals(TurnStatus.RUNNING, turnRepository.savedTurn.getStatus());

        assertSame(messageFactory.userMessageToReturn, persistenceCoordinator.lastUserMessage);
    }

    private static RuntimeExecutionContext buildContext() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hello")
            .build();
        RuntimeExecutionContext context = RuntimeExecutionContext.create(command, null, false);
        context.setResolution(SessionResolutionResult.builder()
            .conversation(Conversation.builder()
                .conversationId("conv-1")
                .title("title")
                .status(ConversationStatus.ACTIVE)
                .activeSessionId("sess-1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build())
            .session(Session.builder()
                .sessionId("sess-1")
                .conversationId("conv-1")
                .status(SessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build())
            .createdConversation(false)
            .createdSession(false)
            .build());
        context.setRunMetadata(RunMetadata.builder()
            .traceId("trace-1")
            .runId("run-1")
            .turnId("turn-1")
            .build());
        return context;
    }

    private static final class StubMessageFactory extends MessageFactory {
        private final UserMessage userMessageToReturn = UserMessage.create(
            "msg-user-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            1L,
            "hello"
        );
        private String lastConversationId;
        private String lastSessionId;
        private String lastTurnId;
        private String lastRunId;
        private String lastContent;

        @Override
        public UserMessage createUserMessage(String conversationId, String sessionId, String turnId, String runId, String content) {
            lastConversationId = conversationId;
            lastSessionId = sessionId;
            lastTurnId = turnId;
            lastRunId = runId;
            lastContent = content;
            return userMessageToReturn;
        }
    }

    private static final class StubTurnRepository implements TurnRepository {
        private Turn savedTurn;

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
            savedTurn = turn;
        }

        @Override
        public void update(Turn turn) {
        }
    }

    private static final class StubPersistenceCoordinator extends PersistenceCoordinator {
        private UserMessage lastUserMessage;

        @Override
        public void persistUserMessage(UserMessage userMessage) {
            lastUserMessage = userMessage;
        }
    }
}
