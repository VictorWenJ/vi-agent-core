package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.tool.ToolResult;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message and tool fact factory.
 */
@Component
public class MessageFactory {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private RunIdentityFactory runIdentityFactory;

    private final Map<String, Long> sessionSequenceCursor = new ConcurrentHashMap<>();

    public UserMessage createUserMessage(String conversationId, String sessionId, String turnId, String runId, String content) {
        long sequence = nextSequenceNo(sessionId);
        return UserMessage.create(runIdentityFactory.nextMessageId(), conversationId, sessionId, turnId, runId, sequence, content);
    }

    public AssistantMessage createAssistantMessage(
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        String content,
        List<AssistantToolCall> toolCalls,
        FinishReason finishReason,
        UsageInfo usage
    ) {
        return createAssistantMessage(
            conversationId,
            sessionId,
            turnId,
            runId,
            nextAssistantMessageId(),
            content,
            toolCalls,
            finishReason,
            usage
        );
    }

    public AssistantMessage createAssistantMessage(
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        String assistantMessageId,
        String content,
        List<AssistantToolCall> toolCalls,
        FinishReason finishReason,
        UsageInfo usage
    ) {
        long sequence = nextSequenceNo(sessionId);
        return AssistantMessage.create(
            assistantMessageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            sequence,
            content,
            toolCalls,
            finishReason,
            usage
        );
    }

    public String resolveToolCallId(ModelToolCall modelToolCall) {
        if (StringUtils.isNotBlank(modelToolCall.getToolCallId())) {
            return modelToolCall.getToolCallId();
        }
        return runIdentityFactory.nextToolCallId();
    }

    public String nextAssistantMessageId() {
        return runIdentityFactory.nextMessageId();
    }

    public String nextToolExecutionId() {
        return runIdentityFactory.nextToolExecutionId();
    }

    public AssistantToolCall createAssistantToolCall(
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        String assistantMessageId,
        ModelToolCall modelToolCall,
        int callIndex
    ) {
        String toolCallId = resolveToolCallId(modelToolCall);
        return AssistantToolCall.builder()
            .toolCallRecordId(runIdentityFactory.nextToolCallRecordId())
            .toolCallId(toolCallId)
            .assistantMessageId(assistantMessageId)
            .conversationId(conversationId)
            .sessionId(sessionId)
            .turnId(turnId)
            .runId(runId)
            .toolName(modelToolCall.getToolName())
            .argumentsJson(StringUtils.defaultIfBlank(modelToolCall.getArgumentsJson(), "{}"))
            .callIndex(callIndex)
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build();
    }

    public ToolCall toToolCall(String turnId, AssistantToolCall assistantToolCall) {
        return ToolCall.builder()
            .toolCallRecordId(assistantToolCall.getToolCallRecordId())
            .toolCallId(assistantToolCall.getToolCallId())
            .toolName(assistantToolCall.getToolName())
            .argumentsJson(StringUtils.defaultIfBlank(assistantToolCall.getArgumentsJson(), "{}"))
            .turnId(turnId)
            .build();
    }

    public ToolMessage createToolMessage(
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        ToolResult toolResult,
        String argumentsJson
    ) {
        long sequence = nextSequenceNo(sessionId);
        return ToolMessage.create(
            runIdentityFactory.nextMessageId(),
            conversationId,
            sessionId,
            turnId,
            runId,
            sequence,
            toolResult.getOutput(),
            toolResult.getToolCallRecordId(),
            toolResult.getToolCallId(),
            toolResult.getToolName(),
            toolResult.isSuccess() ? ToolExecutionStatus.SUCCEEDED : ToolExecutionStatus.FAILED,
            toolResult.getErrorCode(),
            toolResult.getErrorMessage(),
            toolResult.getDurationMs(),
            argumentsJson
        );
    }

    public ToolExecution createToolExecution(
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        ToolResult toolResult,
        ToolMessage toolMessage,
        String argumentsJson,
        Instant startedAt,
        Instant completedAt
    ) {
        String toolExecutionId = runIdentityFactory.nextToolExecutionId();
        return ToolExecution.builder()
            .toolExecutionId(toolExecutionId)
            .toolCallRecordId(toolResult.getToolCallRecordId())
            .toolCallId(toolResult.getToolCallId())
            .toolResultMessageId(toolMessage == null ? null : toolMessage.getMessageId())
            .conversationId(conversationId)
            .sessionId(sessionId)
            .turnId(turnId)
            .runId(runId)
            .toolName(toolResult.getToolName())
            .argumentsJson(argumentsJson)
            .outputText(toolResult.getOutput())
            .outputJson(toolResult.getOutput())
            .status(toolResult.isSuccess() ? ToolExecutionStatus.SUCCEEDED : ToolExecutionStatus.FAILED)
            .errorCode(toolResult.getErrorCode())
            .errorMessage(toolResult.getErrorMessage())
            .durationMs(toolResult.getDurationMs())
            .startedAt(startedAt)
            .completedAt(completedAt)
            .createdAt(Instant.now())
            .build();
    }

    public void clearSessionSequenceCursor(String sessionId) {
        sessionSequenceCursor.remove(sessionId);
    }

    private long nextSequenceNo(String sessionId) {
        return sessionSequenceCursor.compute(sessionId, (ignored, current) -> {
            if (current == null) {
                return messageRepository.nextSequenceNo(sessionId);
            }
            return current + 1;
        });
    }
}
