package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
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
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeEventFactoryTest {

    private final RuntimeEventFactory factory = new RuntimeEventFactory();

    @Test
    void runStartedShouldContainRequiredFields() {
        RuntimeExecutionContext context = buildContext();
        RuntimeEvent event = factory.runStarted(context);

        assertEquals(RuntimeEventType.RUN_STARTED, event.getEventType());
        assertEquals(RunStatus.RUNNING, event.getRunStatus());
        assertEquals("req-1", event.getRequestId());
        assertEquals("conv-1", event.getConversationId());
        assertEquals("sess-1", event.getSessionId());
        assertEquals("turn-1", event.getTurnId());
        assertEquals("run-1", event.getRunId());
    }

    @Test
    void messageDeltaShouldUsePassedAssistantMessageId() {
        RuntimeExecutionContext context = buildContext();
        RuntimeEvent event = factory.messageDelta(context, "msg-assistant-1", "he");

        assertEquals(RuntimeEventType.MESSAGE_DELTA, event.getEventType());
        assertEquals("msg-assistant-1", event.getMessageId());
        assertEquals("he", event.getDelta());
    }

    @Test
    void runCompletedShouldContainUsageFinishReasonAndStatus() {
        RuntimeExecutionContext context = buildContext();
        UsageInfo usageInfo = UsageInfo.builder()
            .inputTokens(10)
            .outputTokens(5)
            .totalTokens(15)
            .provider("deepseek")
            .model("deepseek-chat")
            .build();
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
            usageInfo
        );
        LoopExecutionResult loopExecutionResult = LoopExecutionResult.builder()
            .assistantMessage(assistantMessage)
            .finishReason(FinishReason.STOP)
            .usage(usageInfo)
            .build();

        RuntimeEvent event = factory.runCompleted(context, loopExecutionResult);

        assertEquals(RuntimeEventType.RUN_COMPLETED, event.getEventType());
        assertEquals(RunStatus.COMPLETED, event.getRunStatus());
        assertEquals(FinishReason.STOP, event.getFinishReason());
        assertNotNull(event.getUsage());
        assertEquals(15, event.getUsage().getTotalTokens());
        assertEquals("done", event.getContent());
    }

    @Test
    void runFailedShouldContainErrorPayload() {
        RuntimeExecutionContext context = buildContext();
        RuntimeEvent event = factory.runFailed(context, "RUNTIME-0001", "boom", "SYSTEM", false);

        assertEquals(RuntimeEventType.RUN_FAILED, event.getEventType());
        assertEquals(RunStatus.FAILED, event.getRunStatus());
        assertEquals("RUNTIME-0001", event.getErrorCode());
        assertEquals("boom", event.getErrorMessage());
        assertEquals("SYSTEM", event.getErrorType());
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
        return context;
    }
}
