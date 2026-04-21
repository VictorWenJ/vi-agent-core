package com.vi.agent.core.model.message;

import com.vi.agent.core.model.llm.ModelToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Assistant message.
 */
public final class AssistantMessage extends AbstractMessage {

    private final List<ModelToolCall> toolCalls;

    private AssistantMessage(
        String messageId,
        String turnId,
        long sequenceNo,
        String content,
        List<ModelToolCall> toolCalls,
        Instant createdAt
    ) {
        super(messageId, turnId, MessageRole.ASSISTANT, MessageType.ASSISTANT_OUTPUT, sequenceNo, content, createdAt);
        this.toolCalls = Optional.ofNullable(toolCalls).orElse(Collections.emptyList());
    }

    public static AssistantMessage create(
        String messageId,
        String turnId,
        long sequenceNo,
        String content,
        List<ModelToolCall> toolCalls
    ) {
        return new AssistantMessage(messageId, turnId, sequenceNo, content, toolCalls, Instant.now());
    }

    public static AssistantMessage restore(
        String messageId,
        String turnId,
        long sequenceNo,
        String content,
        List<ModelToolCall> toolCalls,
        Instant createdAt
    ) {
        return new AssistantMessage(messageId, turnId, sequenceNo, content, toolCalls, createdAt);
    }

    public List<ModelToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }
}
