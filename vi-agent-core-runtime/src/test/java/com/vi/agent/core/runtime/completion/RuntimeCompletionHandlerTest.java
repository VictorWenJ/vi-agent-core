package com.vi.agent.core.runtime.completion;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
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
        AgentExecutionResultFactory resultFactory = new AgentExecutionResultFactory();
        TestFieldUtils.setField(handler, "persistenceCoordinator", persistenceCoordinator);
        TestFieldUtils.setField(handler, "agentExecutionResultFactory", resultFactory);

        RuntimeExecutionContext context = buildContext();
        AssistantMessage assistantMessage = AssistantMessage.create("msg-assistant-1", "turn-1", 2L, "done", List.of());
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
        context.setUserMessage(UserMessage.create("msg-user-1", "turn-1", 1L, "hello"));
        context.setRunContext(AgentRunContext.builder()
            .runMetadata(context.getRunMetadata())
            .conversation(conversation)
            .session(session)
            .turn(turn)
            .userInput("hello")
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
}
