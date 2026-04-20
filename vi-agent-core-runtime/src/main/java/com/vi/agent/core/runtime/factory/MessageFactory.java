package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.message.*;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.tool.ToolResultRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for messages and tool records.
 */
@Component
public class MessageFactory {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private RunIdentityFactory runIdentityFactory;

    /**
     * Per-session in-memory sequence cursor to avoid duplicate sequence numbers
     * before messages are flushed to mysql in batch.
     */
    private final Map<String, Long> sessionSequenceCursor = new ConcurrentHashMap<>();

    public UserMessage createUserMessage(String sessionId, String turnId, String content) {
        // 获取当前顺序
        long sequence = nextSequenceNo(sessionId);
        return UserMessage.create(runIdentityFactory.nextMessageId(), turnId, sequence, content);
    }

    public AssistantMessage createAssistantMessage(String sessionId, String turnId, String content, java.util.List<ModelToolCall> toolCalls) {
        return createAssistantMessage(sessionId, turnId, nextAssistantMessageId(), content, toolCalls);
    }

    public AssistantMessage createAssistantMessage(
        String sessionId,
        String turnId,
        String assistantMessageId,
        String content,
        java.util.List<ModelToolCall> toolCalls
    ) {
        long sequence = nextSequenceNo(sessionId);
        return AssistantMessage.create(assistantMessageId, turnId, sequence, content, toolCalls);
    }

    public ToolCallMessage createToolCallMessage(
        String sessionId,
        String turnId,
        String toolCallId,
        String toolName,
        String argumentsJson
    ) {
        long sequence = nextSequenceNo(sessionId);
        return ToolCallMessage.create(runIdentityFactory.nextMessageId(), turnId, sequence, toolCallId, toolName, argumentsJson);
    }

    public ToolResultMessage createToolResultMessage(
        String sessionId,
        String turnId,
        ToolResult toolResult
    ) {
        long sequence = nextSequenceNo(sessionId);
        return ToolResultMessage.create(
            runIdentityFactory.nextMessageId(),
            turnId,
            sequence,
            toolResult.getToolCallId(),
            toolResult.getToolName(),
            toolResult.isSuccess(),
            toolResult.getOutput(),
            toolResult.getErrorCode(),
            toolResult.getErrorMessage(),
            toolResult.getDurationMs()
        );
    }

    public String resolveToolCallId(ModelToolCall modelToolCall) {
        if (modelToolCall.getToolCallId() != null && !modelToolCall.getToolCallId().isBlank()) {
            return modelToolCall.getToolCallId();
        }
        return runIdentityFactory.nextToolCallId();
    }

    public String nextAssistantMessageId() {
        return runIdentityFactory.nextMessageId();
    }

    public ToolCall toToolCall(String turnId, String toolCallId, ModelToolCall modelToolCall) {
        return ToolCall.builder()
            .toolCallId(toolCallId)
            .toolName(modelToolCall.getToolName())
            .argumentsJson(modelToolCall.getArgumentsJson() == null ? "{}" : modelToolCall.getArgumentsJson())
            .turnId(turnId)
            .build();
    }

    public ToolCallRecord createToolCallRecord(
        String conversationId,
        String sessionId,
        String turnId,
        ToolCallMessage message,
        int sequence
    ) {
        return ToolCallRecord.builder()
            .toolCallId(message.getToolCallId())
            .conversationId(conversationId)
            .sessionId(sessionId)
            .turnId(turnId)
            .messageId(message.getMessageId())
            .toolName(message.getToolName())
            .argumentsJson(message.getArgumentsJson())
            .sequenceNo(sequence)
            .status("REQUESTED")
            .createdAt(Instant.now())
            .build();
    }

    public ToolResultRecord createToolResultRecord(
        String conversationId,
        String sessionId,
        String turnId,
        ToolResultMessage message
    ) {
        return ToolResultRecord.builder()
            .toolCallId(message.getToolCallId())
            .conversationId(conversationId)
            .sessionId(sessionId)
            .turnId(turnId)
            .messageId(message.getMessageId())
            .toolName(message.getToolName())
            .success(message.isSuccess())
            .outputJson(message.getContent())
            .errorCode(message.getErrorCode())
            .errorMessage(message.getErrorMessage())
            .durationMs(message.getDurationMs())
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
