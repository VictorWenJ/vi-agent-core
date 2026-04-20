package com.vi.agent.core.app.application.assembler;

import com.vi.agent.core.app.api.dto.response.*;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Assembles API stream event from runtime event.
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
                .toolCallId(runtimeEvent.getToolCall().getToolCallId())
                .toolName(runtimeEvent.getToolCall().getToolName())
                .argumentsJson(runtimeEvent.getToolCall().getArgumentsJson())
                .sequence(runtimeEvent.getToolCall().getSequenceNo())
                .createdAt(runtimeEvent.getToolCall().getCreatedAt())
                .build())
            .toolResult(runtimeEvent.getToolResult() == null ? null : ToolResultPayload.builder()
                .toolCallId(runtimeEvent.getToolResult().getToolCallId())
                .toolName(runtimeEvent.getToolResult().getToolName())
                .success(runtimeEvent.getToolResult().isSuccess())
                .outputJson(runtimeEvent.getToolResult().getOutputJson())
                .errorCode(runtimeEvent.getToolResult().getErrorCode())
                .errorMessage(runtimeEvent.getToolResult().getErrorMessage())
                .durationMs(runtimeEvent.getToolResult().getDurationMs())
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
