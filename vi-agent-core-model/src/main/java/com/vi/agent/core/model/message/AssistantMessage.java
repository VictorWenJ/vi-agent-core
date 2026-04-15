package com.vi.agent.core.model.message;

import com.vi.agent.core.model.tool.ToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 助手消息。
 */
public class AssistantMessage extends BaseMessage {

    /** 助手规划的工具调用列表。 */
    private final List<ToolCall> toolCalls;

    public AssistantMessage(String content) {
        this(content, Collections.emptyList());
    }

    public AssistantMessage(String content, List<ToolCall> toolCalls) {
        super("assistant", content);
        this.toolCalls = new ArrayList<>(toolCalls);
    }

    public List<ToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }
}
