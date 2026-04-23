package com.vi.agent.core.runtime.failure;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionMode;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentExecutionResultFactory;
import com.vi.agent.core.runtime.factory.RuntimeEventFactory;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeFailureHandlerTest {

    @Test
    void shouldReturnFailedResultAndPersistFailureWhenTurnExists() {
        RuntimeFailureHandler handler = new RuntimeFailureHandler();
        StubPersistenceCoordinator persistenceCoordinator = new StubPersistenceCoordinator();
        StubTurnLifecycleService turnLifecycleService = new StubTurnLifecycleService();
        TestFieldUtils.setField(handler, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(handler, "turnLifecycleService", turnLifecycleService);
        TestFieldUtils.setField(handler, "agentExecutionResultFactory", new AgentExecutionResultFactory());

        RuntimeExecutionContext context = buildContext(true, true);
        List<RuntimeEvent> events = new ArrayList<>();
        RuntimeEventSink eventSink = new RuntimeEventSink(context, new RuntimeEventFactory(), events::add);
        AgentRuntimeException input = new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "bad request");

        AgentExecutionResult result = handler.handle(context, input, eventSink);

        assertEquals(RunStatus.FAILED, result.getRunStatus());
        assertEquals(1, persistenceCoordinator.persistFailureCount);
        assertEquals(0, turnLifecycleService.failTurnCount);
        assertEquals(AgentRunState.FAILED, context.getRunContext().getState());
        assertEquals(1, events.size());
        assertEquals(RuntimeEventType.RUN_FAILED, events.get(0).getEventType());
        assertEquals(ErrorCode.INVALID_ARGUMENT.getCode(), events.get(0).getErrorCode());
    }

    @Test
    void shouldWrapThrowableToRuntimeExecutionFailed() {
        RuntimeFailureHandler handler = new RuntimeFailureHandler();
        StubPersistenceCoordinator persistenceCoordinator = new StubPersistenceCoordinator();
        StubTurnLifecycleService turnLifecycleService = new StubTurnLifecycleService();
        TestFieldUtils.setField(handler, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(handler, "turnLifecycleService", turnLifecycleService);
        TestFieldUtils.setField(handler, "agentExecutionResultFactory", new AgentExecutionResultFactory());

        RuntimeExecutionContext context = buildContext(true, true);
        RuntimeEventSink eventSink = new RuntimeEventSink(context, new RuntimeEventFactory(), event -> { });

        AgentExecutionResult result = handler.handle(context, new IllegalStateException("boom"), eventSink);

        assertEquals(RunStatus.FAILED, result.getRunStatus());
        assertEquals(1, persistenceCoordinator.persistFailureCount);
    }

    @Test
    void shouldFallbackToFailTurnWhenNoRunContext() {
        RuntimeFailureHandler handler = new RuntimeFailureHandler();
        StubPersistenceCoordinator persistenceCoordinator = new StubPersistenceCoordinator();
        StubTurnLifecycleService turnLifecycleService = new StubTurnLifecycleService();
        TestFieldUtils.setField(handler, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(handler, "turnLifecycleService", turnLifecycleService);
        TestFieldUtils.setField(handler, "agentExecutionResultFactory", new AgentExecutionResultFactory());

        RuntimeExecutionContext context = buildContext(true, false);
        RuntimeEventSink eventSink = new RuntimeEventSink(context, new RuntimeEventFactory(), event -> { });

        AgentExecutionResult result = handler.handle(context, new IllegalArgumentException("bad"), eventSink);

        assertEquals(RunStatus.FAILED, result.getRunStatus());
        assertEquals(0, persistenceCoordinator.persistFailureCount);
        assertEquals(1, turnLifecycleService.failTurnCount);
        assertEquals("turn-1", turnLifecycleService.lastTurn.getTurnId());
    }

    private static RuntimeExecutionContext buildContext(boolean withTurn, boolean withRunContext) {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hello")
            .build();
        RuntimeExecutionContext context = RuntimeExecutionContext.create(command, null, false);
        Conversation conversation = Conversation.builder()
            .conversationId("conv-1")
            .title("title")
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
        context.setResolution(SessionResolutionResult.builder()
            .conversation(conversation)
            .session(session)
            .createdConversation(false)
            .createdSession(false)
            .build());
        context.setRunMetadata(RunMetadata.builder()
            .traceId("trace-1")
            .runId("run-1")
            .turnId("turn-1")
            .build());
        if (withTurn) {
            context.setTurn(turn);
        }
        if (withRunContext) {
            context.setRunContext(AgentRunContext.builder()
                .runMetadata(context.getRunMetadata())
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
                .state(AgentRunState.STARTED)
                .iteration(0)
                .build());
        }
        return context;
    }

    private static final class StubPersistenceCoordinator extends PersistenceCoordinator {
        private int persistFailureCount = 0;

        @Override
        public void persistFailure(AgentRunContext runContext, String errorCode, String errorMessage) {
            persistFailureCount++;
        }
    }

    private static final class StubTurnLifecycleService extends TurnLifecycleService {
        private int failTurnCount = 0;
        private Turn lastTurn;

        @Override
        public void failTurn(Turn turn, String errorCode, String errorMessage) {
            failTurnCount++;
            lastTurn = turn;
        }
    }
}
