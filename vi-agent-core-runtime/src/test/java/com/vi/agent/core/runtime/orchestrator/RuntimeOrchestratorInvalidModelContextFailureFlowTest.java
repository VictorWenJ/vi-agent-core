package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.SessionLockRepository;
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
import com.vi.agent.core.runtime.completion.RuntimeCompletionHandler;
import com.vi.agent.core.runtime.dedup.RuntimeDeduplicationHandler;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.factory.RuntimeEventFactory;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.event.RuntimeEventSinkFactory;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentExecutionResultFactory;
import com.vi.agent.core.runtime.factory.AgentRunContextFactory;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import com.vi.agent.core.runtime.failure.RuntimeFailureHandler;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.lifecycle.TurnStartResult;
import com.vi.agent.core.runtime.loop.LoopInvocationService;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.scope.RuntimeRunScope;
import com.vi.agent.core.runtime.scope.RuntimeRunScopeManager;
import com.vi.agent.core.runtime.session.SessionResolutionService;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeOrchestratorInvalidModelContextFailureFlowTest {

    @Test
    void executeShouldReturnFailedOnInvalidModelContext() {
        Fixture fixture = new Fixture();

        RuntimeOrchestrator orchestrator = fixture.buildOrchestrator(new ArrayList<>());

        AgentExecutionResult result = orchestrator.execute(fixture.command());

        assertEquals(RunStatus.FAILED, result.getRunStatus());
        assertEquals(1, fixture.persistenceCoordinator.persistFailureCount);
        assertEquals(0, fixture.completionHandler.invocationCount);
        assertEquals(0, fixture.completionHandler.memoryUpdateCount);
    }

    @Test
    void executeStreamingShouldEmitRunFailed() {
        Fixture fixture = new Fixture();
        List<RuntimeEvent> events = new ArrayList<>();
        RuntimeOrchestrator orchestrator = fixture.buildOrchestrator(events);

        orchestrator.executeStreaming(fixture.command(), events::add);

        assertEquals(RuntimeEventType.RUN_FAILED, events.get(events.size() - 1).getEventType());
        assertEquals(1, fixture.persistenceCoordinator.persistFailureCount);
        assertEquals(0, fixture.completionHandler.invocationCount);
        assertEquals(0, fixture.completionHandler.memoryUpdateCount);
    }

    private static final class Fixture {
        private final Conversation conversation = Conversation.builder()
            .conversationId("conv-1")
            .status(ConversationStatus.ACTIVE)
            .activeSessionId("sess-1")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .lastMessageAt(Instant.now())
            .build();

        private final Session session = Session.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .status(SessionStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        private final Turn turn = Turn.builder()
            .turnId("turn-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .requestId("req-1")
            .runId("run-1")
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-user-1")
            .createdAt(Instant.now())
            .build();

        private final UserMessage userMessage = UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "hello");

        private final StubPersistenceCoordinator persistenceCoordinator = new StubPersistenceCoordinator();
        private final StubCompletionHandler completionHandler = new StubCompletionHandler();

        RuntimeExecuteCommand command() {
            return RuntimeExecuteCommand.builder()
                .requestId("req-1")
                .conversationId("conv-1")
                .sessionId("sess-1")
                .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
                .message("hello")
                .build();
        }

        RuntimeOrchestrator buildOrchestrator(List<RuntimeEvent> events) {
            RuntimeFailureHandler failureHandler = new RuntimeFailureHandler();
            TestFieldUtils.setField(failureHandler, "persistenceCoordinator", persistenceCoordinator);
            TestFieldUtils.setField(failureHandler, "agentExecutionResultFactory", new AgentExecutionResultFactory());

            RuntimeEventFactory runtimeEventFactory = new RuntimeEventFactory();
            RuntimeEventSinkFactory eventSinkFactory = new RuntimeEventSinkFactory();
            TestFieldUtils.setField(eventSinkFactory, "runtimeEventFactory", runtimeEventFactory);

            RuntimeOrchestrator orchestrator = new RuntimeOrchestrator();
            TestFieldUtils.setField(orchestrator, "deduplicationHandler", new StubDeduplicationHandler());
            TestFieldUtils.setField(orchestrator, "sessionResolutionService", new StubSessionResolutionService(conversation, session));
            TestFieldUtils.setField(orchestrator, "runIdentityFactory", new StubRunIdentityFactory());
            TestFieldUtils.setField(orchestrator, "runScopeManager", new StubRunScopeManager());
            TestFieldUtils.setField(orchestrator, "turnLifecycleService", new StubTurnStartLifecycleService(turn, userMessage));
            TestFieldUtils.setField(orchestrator, "agentRunContextFactory", new StubAgentRunContextFactory(conversation, session, turn));
            TestFieldUtils.setField(orchestrator, "eventSinkFactory", eventSinkFactory);
            TestFieldUtils.setField(orchestrator, "loopInvocationService", new StubLoopInvocationService());
            TestFieldUtils.setField(orchestrator, "completionHandler", completionHandler);
            TestFieldUtils.setField(orchestrator, "failureHandler", failureHandler);
            return orchestrator;
        }
    }

    private static final class StubDeduplicationHandler extends RuntimeDeduplicationHandler {
        @Override
        public AgentExecutionResult checkAndBuildDedupAgentExecution(RuntimeExecutionContext context) {
            return null;
        }
    }

    private static final class StubSessionResolutionService implements SessionResolutionService {
        private final Conversation conversation;
        private final Session session;

        private StubSessionResolutionService(Conversation conversation, Session session) {
            this.conversation = conversation;
            this.session = session;
        }

        @Override
        public SessionResolutionResult judgeSessionResolutionMode(RuntimeExecuteCommand command) {
            return SessionResolutionResult.builder()
                .conversation(conversation)
                .session(session)
                .createdConversation(false)
                .createdSession(false)
                .build();
        }
    }

    private static final class StubRunIdentityFactory extends RunIdentityFactory {
        @Override
        public RunMetadata createRunMetadata() {
            return RunMetadata.builder()
                .traceId("trace-1")
                .runId("run-1")
                .turnId("turn-1")
                .build();
        }
    }

    private static final class StubRunScopeManager extends RuntimeRunScopeManager {
        @Override
        public RuntimeRunScope open(RuntimeExecutionContext context) {
            return new RuntimeRunScope(
                context.sessionId(),
                context.runId(),
                null,
                new NoopSessionLockRepository(),
                new MessageFactory()
            );
        }
    }

    private static final class NoopSessionLockRepository implements SessionLockRepository {
        @Override
        public boolean tryLock(String sessionId, String runId, Duration ttl) {
            return true;
        }

        @Override
        public void unlock(String sessionId, String runId) {
        }
    }

    private static final class StubTurnStartLifecycleService extends TurnLifecycleService {
        private final Turn turn;
        private final UserMessage userMessage;

        private StubTurnStartLifecycleService(Turn turn, UserMessage userMessage) {
            this.turn = turn;
            this.userMessage = userMessage;
        }

        @Override
        public TurnStartResult startTurn(RuntimeExecutionContext context) {
            return new TurnStartResult(turn, userMessage);
        }
    }

    private static final class StubAgentRunContextFactory extends AgentRunContextFactory {
        private final Conversation conversation;
        private final Session session;
        private final Turn turn;

        private StubAgentRunContextFactory(Conversation conversation, Session session, Turn turn) {
            this.conversation = conversation;
            this.session = session;
            this.turn = turn;
        }

        @Override
        public AgentRunContext create(RuntimeExecutionContext context) {
            return AgentRunContext.builder()
                .runMetadata(context.getRunMetadata())
                .conversation(conversation)
                .session(session)
                .turn(turn)
                .userInput("hello")
                .workingMessages(new ArrayList<>(List.of(context.getUserMessage())))
                .availableTools(List.of())
                .state(AgentRunState.STARTED)
                .iteration(0)
                .build();
        }
    }

    private static final class StubLoopInvocationService extends LoopInvocationService {
        @Override
        public com.vi.agent.core.model.runtime.LoopExecutionResult process(RuntimeExecutionContext context, RuntimeEventSink eventSink) {
            throw new AgentRuntimeException(ErrorCode.INVALID_MODEL_CONTEXT_MESSAGE, "invalid model context chain");
        }
    }

    private static final class StubCompletionHandler extends RuntimeCompletionHandler {
        private int invocationCount;
        private int memoryUpdateCount;

        @Override
        public AgentExecutionResult complete(RuntimeExecutionContext context, RuntimeEventSink eventSink) {
            invocationCount++;
            memoryUpdateCount++;
            throw new IllegalStateException("should not be called");
        }
    }

    private static final class StubPersistenceCoordinator extends PersistenceCoordinator {
        private int persistFailureCount;

        @Override
        public void persistFailure(AgentRunContext runContext, String errorCode, String errorMessage) {
            persistFailureCount++;
        }
    }
}

