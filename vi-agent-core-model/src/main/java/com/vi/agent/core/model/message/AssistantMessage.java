package com.vi.agent.core.model.message;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 助手输出消息。
 */
public final class AssistantMessage extends AbstractMessage {

    private final List<AssistantToolCall> toolCalls;

    private final FinishReason finishReason;

    private final UsageInfo usage;

    private AssistantMessage(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        MessageStatus status,
        String contentText,
        List<AssistantToolCall> toolCalls,
        FinishReason finishReason,
        UsageInfo usage,
        Instant createdAt
    ) {
        super(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            MessageRole.ASSISTANT,
            MessageType.ASSISTANT_OUTPUT,
            sequenceNo,
            status,
            contentText,
            createdAt
        );
        this.toolCalls = toolCalls == null || toolCalls.isEmpty() ? List.of() : List.copyOf(toolCalls);
        this.finishReason = finishReason;
        this.usage = usage;
    }

    public static AssistantMessage create(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        String contentText,
        List<AssistantToolCall> toolCalls,
        FinishReason finishReason,
        UsageInfo usage
    ) {
        return new AssistantMessage(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            sequenceNo,
            MessageStatus.COMPLETED,
            contentText,
            toolCalls,
            finishReason,
            usage,
            Instant.now()
        );
    }

    public static AssistantMessage restore(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        MessageStatus status,
        String contentText,
        List<AssistantToolCall> toolCalls,
        FinishReason finishReason,
        UsageInfo usage,
        Instant createdAt
    ) {
        return new AssistantMessage(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            sequenceNo,
            status,
            contentText,
            toolCalls,
            finishReason,
            usage,
            createdAt
        );
    }

    public List<AssistantToolCall> getToolCalls() {
        return Collections.unmodifiableList(new ArrayList<>(toolCalls));
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }

    public UsageInfo getUsage() {
        return usage;
    }
}
