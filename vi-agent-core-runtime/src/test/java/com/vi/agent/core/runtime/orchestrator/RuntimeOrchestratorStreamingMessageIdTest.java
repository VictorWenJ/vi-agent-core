package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.SessionLockRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionMode;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.engine.AssistantStreamListener;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import com.vi.agent.core.runtime.lifecycle.TurnDedupResult;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.mdc.RuntimeMdcManager;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.session.SessionResolutionService;
import com.vi.agent.core.runtime.state.SessionStateLoader;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuntimeOrchestratorStreamingMessageIdTest {

    @Test
    void streamingMessageEventsShouldUseAssistantMessageId() throws Exception {
        RuntimeOrchestrator orchestrator = new RuntimeOrchestrator();

        Conversation conversation = Conversation.builder()
            .conversationId("conv-1")
            .title("t")
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

        SessionResolutionService sessionResolutionService = command -> SessionResolutionResult.builder()
            .conversation(conversation)
            .session(session)
            .createdConversation(false)
            .createdSession(false)
            .build();

        RunIdentityFactory runIdentityFactory = new RunIdentityFactory() {
            @Override
            public RunMetadata createRunMetadata() {
                return RunMetadata.builder()
                    .traceId("trace-1")
                    .runId("run-1")
                    .turnId("turn-1")
                    .build();
            }
        };

        TurnLifecycleService turnLifecycleService = new TurnLifecycleService() {
            @Override
            public TurnDedupResult findAndBuildByRequestId(String requestId) {
                return null;
            }

            @Override
            public Turn createRunningTurn(
                String turnId,
                String conversationId,
                String sessionId,
                String requestId,
                String runId,
                String userMessageId
            ) {
                return Turn.builder()
                    .turnId(turnId)
                    .conversationId(conversationId)
                    .sessionId(sessionId)
                    .requestId(requestId)
                    .runId(runId)
                    .status(TurnStatus.RUNNING)
                    .userMessageId(userMessageId)
                    .createdAt(Instant.now())
                    .build();
            }

            @Override
            public boolean existsRunningTurn(String sessionId) {
                return false;
            }
        };

        SessionStateLoader sessionStateLoader = new SessionStateLoader() {
            @Override
            public List<Message> load(String conversationId, String sessionId) {
                return new ArrayList<>();
            }
        };

        MessageFactory messageFactory = new MessageFactory() {
            @Override
            public UserMessage createUserMessage(String sessionId, String turnId, String content) {
                return UserMessage.create("user-msg-1", turnId, 1L, content);
            }

            @Override
            public void clearSessionSequenceCursor(String sessionId) {
                // no-op for unit test
            }
        };

        PersistenceCoordinator persistenceCoordinator = new PersistenceCoordinator() {
            @Override
            public void persistUserMessage(String conversationId, String sessionId, Message userMessage) {
                // no-op for unit test
            }

            @Override
            public void persistSuccess(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
                // no-op for unit test
            }
        };

        AgentLoopEngine loopEngine = new AgentLoopEngine() {
            @Override
            public LoopExecutionResult run(AgentRunContext runContext) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public LoopExecutionResult runStreaming(AgentRunContext runContext, AssistantStreamListener streamListener) {
                AssistantMessage assistantMessage = AssistantMessage.create(
                    "assistant-msg-1",
                    runContext.getTurn().getTurnId(),
                    2L,
                    "hello",
                    List.of()
                );
                streamListener.onMessageStarted(assistantMessage.getMessageId());
                streamListener.onMessageDelta(assistantMessage.getMessageId(), "he");
                streamListener.onMessageCompleted(assistantMessage, FinishReason.STOP);

                return LoopExecutionResult.builder()
                    .assistantMessage(assistantMessage)
                    .appendedMessages(List.of(assistantMessage))
                    .toolCalls(List.of())
                    .toolResults(List.of())
                    .finishReason(FinishReason.STOP)
                    .usage(UsageInfo.empty())
                    .build();
            }
        };

        ToolGateway toolGateway = new ToolGateway() {
            @Override
            public com.vi.agent.core.model.tool.ToolResult execute(com.vi.agent.core.model.tool.ToolCall toolCall) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public List<com.vi.agent.core.model.tool.ToolDefinition> listDefinitions() {
                return List.of();
            }
        };

        SessionLockRepository sessionLockRepository = new SessionLockRepository() {
            @Override
            public boolean tryLock(String sessionId, String runId, Duration ttl) {
                return true;
            }

            @Override
            public void unlock(String sessionId, String runId) {
                // no-op for unit test
            }
        };

        setField(orchestrator, "sessionResolutionService", sessionResolutionService);
        setField(orchestrator, "runIdentityFactory", runIdentityFactory);
        setField(orchestrator, "turnLifecycleService", turnLifecycleService);
        setField(orchestrator, "sessionStateLoader", sessionStateLoader);
        setField(orchestrator, "messageFactory", messageFactory);
        setField(orchestrator, "persistenceCoordinator", persistenceCoordinator);
        setField(orchestrator, "runtimeMdcManager", new RuntimeMdcManager());
        setField(orchestrator, "agentLoopEngine", loopEngine);
        setField(orchestrator, "toolGateway", toolGateway);
        setField(orchestrator, "sessionLockRepository", sessionLockRepository);

        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hi")
            .build();

        List<RuntimeEvent> events = new ArrayList<>();
        orchestrator.executeStreaming(command, events::add);

        RuntimeEvent started = singleEvent(events, RuntimeEventType.MESSAGE_STARTED);
        RuntimeEvent delta = singleEvent(events, RuntimeEventType.MESSAGE_DELTA);
        RuntimeEvent completed = singleEvent(events, RuntimeEventType.MESSAGE_COMPLETED);

        assertEquals(started.getMessageId(), delta.getMessageId());
        assertEquals(delta.getMessageId(), completed.getMessageId());
        assertEquals("assistant-msg-1", started.getMessageId());
        assertNotEquals("user-msg-1", started.getMessageId());
    }

    private static RuntimeEvent singleEvent(List<RuntimeEvent> events, RuntimeEventType eventType) {
        return events.stream()
            .filter(event -> event.getEventType() == eventType)
            .findFirst()
            .orElseThrow();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = RuntimeOrchestrator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

