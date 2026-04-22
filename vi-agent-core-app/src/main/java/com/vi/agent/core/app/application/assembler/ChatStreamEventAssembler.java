package com.vi.agent.core.app.application.assembler;

import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import com.vi.agent.core.app.api.dto.response.ErrorPayload;
import com.vi.agent.core.app.api.dto.response.StreamEventType;
import com.vi.agent.core.app.api.dto.response.ToolCallPayload;
import com.vi.agent.core.app.api.dto.response.ToolResultPayload;
import com.vi.agent.core.app.api.dto.response.UsageInfo;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 组装流式响应事件。
 */
@Component
public class ChatStreamEventAssembler {

    public ChatStreamEvent toApiEvent(RuntimeEvent runtimeEvent) {
        return ChatStreamEvent.builder()
            .eventType(toEventType(runtimeEvent.getEventType()))
            .runStatus(runtimeEvent.getRunStatus())
            .requestId(runtimeEvent.getRequestId())
            .conversationId(runtimeEvent.getConversationId())
            .sessionId(runtimeEvent.getSessionId())
            .turnId(runtimeEvent.getTurnId())
            .runId(runtimeEvent.getRunId())
            .messageId(runtimeEvent.getMessageId())
            .delta(runtimeEvent.getDelta())
            .content(runtimeEvent.getContent())
            .finishReason(runtimeEvent.getFinishReason())
            .usage(runtimeEvent.getUsage() == null ? null : UsageInfo.builder()
                .inputTokens(runtimeEvent.getUsage().getInputTokens())
                .outputTokens(runtimeEvent.getUsage().getOutputTokens())
                .totalTokens(runtimeEvent.getUsage().getTotalTokens())
                .provider(runtimeEvent.getUsage().getProvider())
                .model(runtimeEvent.getUsage().getModel())
                .build())
            .toolCall(runtimeEvent.getToolCall() == null ? null : ToolCallPayload.builder()
                .toolCallRecordId(runtimeEvent.getToolCall().getToolCallRecordId())
                .toolCallId(runtimeEvent.getToolCall().getToolCallId())
                .assistantMessageId(runtimeEvent.getToolCall().getAssistantMessageId())
                .toolName(runtimeEvent.getToolCall().getToolName())
                .argumentsJson(runtimeEvent.getToolCall().getArgumentsJson())
                .callIndex(runtimeEvent.getToolCall().getCallIndex())
                .status(runtimeEvent.getToolCall().getStatus() == null ? null : runtimeEvent.getToolCall().getStatus().name())
                .createdAt(runtimeEvent.getToolCall().getCreatedAt())
                .build())
            .toolResult(runtimeEvent.getToolResult() == null ? null : ToolResultPayload.builder()
                .toolExecutionId(runtimeEvent.getToolResult().getToolExecutionId())
                .toolCallRecordId(runtimeEvent.getToolResult().getToolCallRecordId())
                .toolCallId(runtimeEvent.getToolResult().getToolCallId())
                .toolResultMessageId(runtimeEvent.getToolResult().getToolResultMessageId())
                .toolName(runtimeEvent.getToolResult().getToolName())
                .argumentsJson(runtimeEvent.getToolResult().getArgumentsJson())
                .outputText(runtimeEvent.getToolResult().getOutputText())
                .outputJson(runtimeEvent.getToolResult().getOutputJson())
                .status(runtimeEvent.getToolResult().getStatus() == null ? null : runtimeEvent.getToolResult().getStatus().name())
                .errorCode(runtimeEvent.getToolResult().getErrorCode())
                .errorMessage(runtimeEvent.getToolResult().getErrorMessage())
                .durationMs(runtimeEvent.getToolResult().getDurationMs())
                .startedAt(runtimeEvent.getToolResult().getStartedAt())
                .completedAt(runtimeEvent.getToolResult().getCompletedAt())
                .createdAt(runtimeEvent.getToolResult().getCreatedAt())
                .build())
            .error(runtimeEvent.getErrorCode() == null ? null : ErrorPayload.builder()
                .errorCode(runtimeEvent.getErrorCode())
                .errorMessage(runtimeEvent.getErrorMessage())
                .errorType(runtimeEvent.getErrorType())
                .retryable(runtimeEvent.isRetryable())
                .createdAt(Instant.now())
                .build())
            .build();
    }

    private StreamEventType toEventType(RuntimeEventType runtimeEventType) {
        return switch (runtimeEventType) {
            case RUN_STARTED -> StreamEventType.RUN_STARTED;
            case MESSAGE_STARTED -> StreamEventType.MESSAGE_STARTED;
            case MESSAGE_DELTA -> StreamEventType.MESSAGE_DELTA;
            case TOOL_CALL -> StreamEventType.TOOL_CALL;
            case TOOL_RESULT -> StreamEventType.TOOL_RESULT;
            case MESSAGE_COMPLETED -> StreamEventType.MESSAGE_COMPLETED;
            case RUN_COMPLETED -> StreamEventType.RUN_COMPLETED;
            case RUN_FAILED -> StreamEventType.RUN_FAILED;
        };
    }
}
