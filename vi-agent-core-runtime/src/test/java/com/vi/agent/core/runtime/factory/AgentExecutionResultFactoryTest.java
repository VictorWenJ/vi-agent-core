package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
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
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentExecutionResultFactoryTest {

    private final AgentExecutionResultFactory factory = new AgentExecutionResultFactory();

    @Test
    void completedShouldBuildFullResult() {
        RuntimeExecutionContext context = buildContext();
        AssistantMessage assistantMessage = AssistantMessage.create("msg-assistant-1", "turn-1", 2L, "done", List.of());
        LoopExecutionResult loopExecutionResult = LoopExecutionResult.builder()
            .assistantMessage(assistantMessage)
            .finishReason(FinishReason.STOP)
            .usage(UsageInfo.empty())
            .build();

        AgentExecutionResult result = factory.completed(context, loopExecutionResult);

        assertEquals(RunStatus.COMPLETED, result.getRunStatus());
        assertEquals("req-1", result.getRequestId());
        assertEquals("conv-1", result.getConversationId());
        assertEquals("sess-1", result.getSessionId());
        assertEquals("turn-1", result.getTurnId());
        assertEquals("msg-user-1", result.getUserMessageId());
        assertEquals("msg-assistant-1", result.getAssistantMessageId());
        assertEquals("run-1", result.getRunId());
        assertEquals(FinishReason.STOP, result.getFinishReason());
        assertNotNull(result.getUsage());
    }

    @Test
    void processingShouldBuildRunningResult() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-2")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hello")
            .build();
        Turn turn = Turn.builder()
            .turnId("turn-2")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .requestId("req-2")
            .runId("run-2")
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-user-2")
            .createdAt(Instant.now())
            .build();

        AgentExecutionResult result = factory.processing(command, turn);

        assertEquals(RunStatus.RUNNING, result.getRunStatus());
        assertEquals("req-2", result.getRequestId());
        assertEquals("turn-2", result.getTurnId());
        assertEquals("run-2", result.getRunId());
    }

    @Test
    void completedFromTurnShouldBuildResultFromPersistedTurn() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-3")
            .message("hello")
            .build();
        Turn turn = Turn.builder()
            .turnId("turn-3")
            .conversationId("conv-3")
            .sessionId("sess-3")
            .requestId("req-3")
            .runId("run-3")
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-user-3")
            .assistantMessageId(null)
            .createdAt(Instant.now())
            .build();
        turn.markCompleted(FinishReason.STOP, UsageInfo.empty(), Instant.now(), "msg-assistant-3");
        AssistantMessage assistantMessage = AssistantMessage.create("msg-assistant-3", "turn-3", 2L, "ok", List.of());

        AgentExecutionResult result = factory.completedFromTurn(command, turn, assistantMessage);

        assertEquals(RunStatus.COMPLETED, result.getRunStatus());
        assertEquals("conv-3", result.getConversationId());
        assertEquals("sess-3", result.getSessionId());
        assertEquals("turn-3", result.getTurnId());
        assertEquals("msg-user-3", result.getUserMessageId());
        assertEquals("msg-assistant-3", result.getAssistantMessageId());
        assertEquals("run-3", result.getRunId());
        assertEquals("ok", result.getFinalAssistantMessage().getContent());
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
        context.setTurn(Turn.builder()
            .turnId("turn-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .requestId("req-1")
            .runId("run-1")
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-user-1")
            .createdAt(Instant.now())
            .build());
        context.setUserMessage(UserMessage.create("msg-user-1", "turn-1", 1L, "hello"));
        return context;
    }
}
