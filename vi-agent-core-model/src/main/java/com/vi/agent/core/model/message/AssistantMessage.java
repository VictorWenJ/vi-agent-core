package com.vi.agent.core.model.message;

import com.vi.agent.core.model.tool.ToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 助手消息。
 */
public class AssistantMessage extends AbstractMessage {

    /** 助手规划的工具调用列表。 */
    private final List<ToolCall> toolCalls;

    public AssistantMessage(String content) {
        this(null, content, Collections.emptyList(), Instant.now());
    }

    public AssistantMessage(String content, List<ToolCall> toolCalls) {
        this(null, content, toolCalls, Instant.now());
    }

    public AssistantMessage(String messageId, String content, List<ToolCall> toolCalls) {
        this(messageId, content, toolCalls, Instant.now());
    }

    public AssistantMessage(String messageId, String content, List<ToolCall> toolCalls, Instant createdAt) {
        super(messageId, "assistant", content, createdAt);
        this.toolCalls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls);
    }

    public List<ToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }
}
