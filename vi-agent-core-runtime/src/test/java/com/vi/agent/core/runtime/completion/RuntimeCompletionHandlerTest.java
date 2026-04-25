package com.vi.agent.core.runtime.completion;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextBuildResult;
import com.vi.agent.core.model.context.WorkingContextMetadata;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
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
import com.vi.agent.core.runtime.memory.SessionMemoryCoordinator;
import com.vi.agent.core.runtime.memory.SessionMemoryUpdateCommand;
import com.vi.agent.core.runtime.memory.SessionMemoryUpdateResult;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RuntimeCompletionHandlerTest {

    @Test
    void completeShouldPersistEmitRunCompletedAndReturnCompletedResult() {
        RuntimeCompletionHandler handler = new RuntimeCompletionHandler();
        StubPersistenceCoordinator persistenceCoordinator = new StubPersistenceCoordinator();
        StubSessionMemoryCoordinator sessionMemoryCoordinator = new StubSessionMemoryCoordinator();
        AgentExecutionResultFactory resultFactory = new AgentExecutionResultFactory();
        TestFieldUtils.setField(handler, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(handler, "agentExecutionResultFactory", resultFactory);
        TestFieldUtils.setField(handler, "sessionMemoryCoordinator", sessionMemoryCoordinator);

        RuntimeExecutionContext context = buildContext();
        AssistantMessage assistantMessage = AssistantMessage.create(
            "msg-assistant-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            2L,
            "done",
            List.of(),
            FinishReason.STOP,
            UsageInfo.empty()
        );
        LoopExecutionResult loopExecutionResult = LoopExecutionResult.builder()
            .assistantMessage(assistantMessage)
            .finishReason(FinishReason.STOP)
            .usage(UsageInfo.empty())
            .build();
        context.setLoopResult(loopExecutionResult);

        List<RuntimeEvent> events = new ArrayList<>();
        RuntimeEventSink sink = new RuntimeEventSink(context, new RuntimeEventFactory(), events::add);

        AgentExecutionResult result = handler.complete(context, sink);

        assertSame(context.getRunContext(), persistenceCoordinator.lastRunContext);
        assertSame(loopExecutionResult, persistenceCoordinator.lastLoopExecutionResult);
        assertEquals(1, events.size());
        assertEquals(RuntimeEventType.RUN_COMPLETED, events.get(0).getEventType());
        assertEquals(RunStatus.COMPLETED, result.getRunStatus());
        assertEquals("msg-assistant-1", result.getAssistantMessageId());
        assertEquals(AgentRunState.COMPLETED, context.getRunContext().getState());
        assertEquals(1, sessionMemoryCoordinator.invocationCount);
        assertEquals("msg-user-1", sessionMemoryCoordinator.lastCommand.getCurrentUserMessageId());
        assertEquals("msg-assistant-1", sessionMemoryCoordinator.lastCommand.getAssistantMessageId());
        assertEquals("wctx-1", sessionMemoryCoordinator.lastCommand.getWorkingContextSnapshotId());
        assertEquals(AgentMode.GENERAL, sessionMemoryCoordinator.lastCommand.getAgentMode());
    }

    @Test
    void completeShouldIgnorePostTurnMemoryUpdateFailureAndReturnCompletedResult() {
        RuntimeCompletionHandler handler = new RuntimeCompletionHandler();
        StubSessionMemoryCoordinator sessionMemoryCoordinator = new StubSessionMemoryCoordinator();
        sessionMemoryCoordinator.throwOnUpdate = true;
        TestFieldUtils.setField(handler, "persistenceCoordinator", new StubPersistenceCoordinator());
        TestFieldUtils.setField(handler, "agentExecutionResultFactory", new AgentExecutionResultFactory());
        TestFieldUtils.setField(handler, "sessionMemoryCoordinator", sessionMemoryCoordinator);

        RuntimeExecutionContext context = buildContext();
        AssistantMessage assistantMessage = AssistantMessage.create(
            "msg-assistant-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            2L,
            "done",
            List.of(),
            FinishReason.STOP,
            UsageInfo.empty()
        );
        LoopExecutionResult loopExecutionResult = LoopExecutionResult.builder()
            .assistantMessage(assistantMessage)
            .finishReason(FinishReason.STOP)
            .usage(UsageInfo.empty())
            .build();
        context.setLoopResult(loopExecutionResult);

        AgentExecutionResult result = handler.complete(context, new RuntimeEventSink(context, new RuntimeEventFactory(), event -> { }));

        assertEquals(RunStatus.COMPLETED, result.getRunStatus());
        assertEquals(1, sessionMemoryCoordinator.invocationCount);
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
        context.setTurn(turn);
        context.setUserMessage(UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "hello"));
        context.setRunContext(AgentRunContext.builder()
            .runMetadata(context.getRunMetadata())
            .conversation(conversation)
            .session(session)
            .turn(turn)
            .userInput("hello")
            .agentMode(AgentMode.GENERAL)
            .workingContextBuildResult(WorkingContextBuildResult.builder()
                .context(WorkingContext.builder()
                    .metadata(WorkingContextMetadata.builder()
                        .workingContextSnapshotId("wctx-1")
                        .conversationId("conv-1")
                        .sessionId("sess-1")
                        .turnId("turn-1")
                        .runId("run-1")
                        .checkpointTrigger(CheckpointTrigger.BEFORE_FIRST_MODEL_CALL)
                        .agentMode(AgentMode.GENERAL)
                        .build())
                    .build())
                .build())
            .workingMessages(new ArrayList<>())
            .availableTools(List.of())
            .state(AgentRunState.STARTED)
            .iteration(0)
            .build());
        return context;
    }

    private static final class StubPersistenceCoordinator extends PersistenceCoordinator {
        private AgentRunContext lastRunContext;
        private LoopExecutionResult lastLoopExecutionResult;

        @Override
        public void persistSuccess(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
            lastRunContext = runContext;
            lastLoopExecutionResult = loopExecutionResult;
        }
    }

    private static final class StubSessionMemoryCoordinator extends SessionMemoryCoordinator {
        private int invocationCount;
        private boolean throwOnUpdate;
        private SessionMemoryUpdateCommand lastCommand;

        @Override
        public SessionMemoryUpdateResult updateAfterTurn(SessionMemoryUpdateCommand command) {
            invocationCount++;
            lastCommand = command;
            if (throwOnUpdate) {
                throw new IllegalStateException("memory failed");
            }
            return SessionMemoryUpdateResult.builder().success(true).build();
        }
    }
}

