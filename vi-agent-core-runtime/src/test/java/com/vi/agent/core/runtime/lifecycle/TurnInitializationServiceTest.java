package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.message.UserMessage;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TurnInitializationServiceTest {

    @Test
    void startShouldCreateUserMessageCreateTurnAndPersistUserMessage() {
        TurnInitializationService service = new TurnInitializationService();
        StubMessageFactory messageFactory = new StubMessageFactory();
        StubTurnLifecycleService turnLifecycleService = new StubTurnLifecycleService();
        StubPersistenceCoordinator persistenceCoordinator = new StubPersistenceCoordinator();

        TestFieldUtils.setField(service, "messageFactory", messageFactory);
        TestFieldUtils.setField(service, "turnLifecycleService", turnLifecycleService);
        TestFieldUtils.setField(service, "persistenceCoordinator", persistenceCoordinator);

        RuntimeExecutionContext context = buildContext();
        TurnStartResult turnStartResult = service.start(context);

        assertNotNull(turnStartResult);
        assertSame(messageFactory.userMessageToReturn, turnStartResult.getUserMessage());
        assertSame(turnLifecycleService.turnToReturn, turnStartResult.getTurn());

        assertEquals("sess-1", messageFactory.lastSessionId);
        assertEquals("turn-1", messageFactory.lastTurnId);
        assertEquals("hello", messageFactory.lastContent);

        assertEquals("turn-1", turnLifecycleService.lastTurnId);
        assertEquals("conv-1", turnLifecycleService.lastConversationId);
        assertEquals("sess-1", turnLifecycleService.lastSessionId);
        assertEquals("req-1", turnLifecycleService.lastRequestId);
        assertEquals("run-1", turnLifecycleService.lastRunId);
        assertEquals("msg-user-1", turnLifecycleService.lastUserMessageId);

        assertEquals("conv-1", persistenceCoordinator.lastConversationId);
        assertEquals("sess-1", persistenceCoordinator.lastSessionId);
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
        private final UserMessage userMessageToReturn = UserMessage.create("msg-user-1", "turn-1", 1L, "hello");
        private String lastSessionId;
        private String lastTurnId;
        private String lastContent;

        @Override
        public UserMessage createUserMessage(String sessionId, String turnId, String content) {
            lastSessionId = sessionId;
            lastTurnId = turnId;
            lastContent = content;
            return userMessageToReturn;
        }
    }

    private static final class StubTurnLifecycleService extends TurnLifecycleService {
        private final Turn turnToReturn = Turn.builder()
            .turnId("turn-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .requestId("req-1")
            .runId("run-1")
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-user-1")
            .createdAt(Instant.now())
            .build();

        private String lastTurnId;
        private String lastConversationId;
        private String lastSessionId;
        private String lastRequestId;
        private String lastRunId;
        private String lastUserMessageId;

        @Override
        public Turn createRunningTurn(
            String turnId,
            String conversationId,
            String sessionId,
            String requestId,
            String runId,
            String userMessageId
        ) {
            lastTurnId = turnId;
            lastConversationId = conversationId;
            lastSessionId = sessionId;
            lastRequestId = requestId;
            lastRunId = runId;
            lastUserMessageId = userMessageId;
            return turnToReturn;
        }
    }

    private static final class StubPersistenceCoordinator extends PersistenceCoordinator {
        private String lastConversationId;
        private String lastSessionId;
        private UserMessage lastUserMessage;

        @Override
        public void persistUserMessage(String conversationId, String sessionId, com.vi.agent.core.model.message.Message userMessage) {
            lastConversationId = conversationId;
            lastSessionId = sessionId;
            lastUserMessage = (UserMessage) userMessage;
        }
    }
}
