package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import org.springframework.stereotype.Component;

/**
 * 统一构建 Runtime 事件对象。
 */
@Component
public class RuntimeEventFactory {

    public RuntimeEvent runStarted(RuntimeExecutionContext context) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.RUN_STARTED)
            .runStatus(RunStatus.RUNNING)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .build();
    }

    public RuntimeEvent messageStarted(RuntimeExecutionContext context, String messageId) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.MESSAGE_STARTED)
            .runStatus(RunStatus.RUNNING)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .messageId(messageId)
            .build();
    }

    public RuntimeEvent messageDelta(RuntimeExecutionContext context, String messageId, String delta) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.MESSAGE_DELTA)
            .runStatus(RunStatus.RUNNING)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .messageId(messageId)
            .delta(delta)
            .build();
    }

    public RuntimeEvent messageCompleted(
        RuntimeExecutionContext context,
        AssistantMessage assistantMessage,
        FinishReason finishReason,
        UsageInfo usage
    ) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.MESSAGE_COMPLETED)
            .runStatus(RunStatus.RUNNING)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .messageId(assistantMessage == null ? null : assistantMessage.getMessageId())
            .content(assistantMessage == null ? null : assistantMessage.getContent())
            .finishReason(finishReason)
            .usage(usage)
            .build();
    }

    public RuntimeEvent toolCall(RuntimeExecutionContext context, ToolCallRecord toolCallRecord) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.TOOL_CALL)
            .runStatus(RunStatus.RUNNING)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .messageId(toolCallRecord == null ? null : toolCallRecord.getMessageId())
            .toolCall(toolCallRecord)
            .build();
    }

    public RuntimeEvent toolResult(RuntimeExecutionContext context, ToolResultRecord toolResultRecord) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.TOOL_RESULT)
            .runStatus(RunStatus.RUNNING)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .messageId(toolResultRecord == null ? null : toolResultRecord.getMessageId())
            .toolResult(toolResultRecord)
            .build();
    }

    public RuntimeEvent runCompleted(RuntimeExecutionContext context, LoopExecutionResult loopExecutionResult) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.RUN_COMPLETED)
            .runStatus(RunStatus.COMPLETED)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .finishReason(loopExecutionResult == null ? null : loopExecutionResult.getFinishReason())
            .usage(loopExecutionResult == null ? null : loopExecutionResult.getUsage())
            .content(loopExecutionResult == null || loopExecutionResult.getAssistantMessage() == null
                ? null : loopExecutionResult.getAssistantMessage().getContent())
            .build();
    }

    public RuntimeEvent runFailed(
        RuntimeExecutionContext context,
        String errorCode,
        String errorMessage,
        String errorType,
        boolean retryable
    ) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.RUN_FAILED)
            .runStatus(RunStatus.FAILED)
            .requestId(context.requestId())
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .errorType(errorType)
            .retryable(retryable)
            .build();
    }

    public RuntimeEvent processing(RuntimeExecuteCommand command, Turn turn) {
        return RuntimeEvent.builder()
            .eventType(RuntimeEventType.RUN_STARTED)
            .runStatus(RunStatus.RUNNING)
            .requestId(command == null ? null : command.getRequestId())
            .conversationId(turn == null ? null : turn.getConversationId())
            .sessionId(turn == null ? null : turn.getSessionId())
            .turnId(turn == null ? null : turn.getTurnId())
            .runId(turn == null ? null : turn.getRunId())
            .content("processing")
            .build();
    }
}

