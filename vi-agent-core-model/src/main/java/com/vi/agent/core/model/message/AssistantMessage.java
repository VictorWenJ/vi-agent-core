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

    private AssistantMessage(String messageId, String turnId, String content, List<ToolCall> toolCalls, Instant createdAt) {
        super(messageId, turnId, "assistant", content, createdAt);
        this.toolCalls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls);
    }

    /**
     * 运行时新建助手消息。
     *
     * @param messageId 消息 ID（可为空，为空时自动生成）
     * @param turnId 轮次 ID
     * @param content 文本内容
     * @param toolCalls 工具调用规划
     * @return 助手消息
     */
    public static AssistantMessage create(String messageId, String turnId, String content, List<ToolCall> toolCalls) {
        return new AssistantMessage(messageId, turnId, content, toolCalls, Instant.now());
    }

    /**
     * 持久化恢复助手消息。
     *
     * @param messageId 消息 ID
     * @param turnId 轮次 ID
     * @param content 文本内容
     * @param toolCalls 工具调用规划
     * @param createdAt 创建时间
     * @return 助手消息
     */
    public static AssistantMessage restore(
        String messageId,
        String turnId,
        String content,
        List<ToolCall> toolCalls,
        Instant createdAt
    ) {
        return new AssistantMessage(messageId, turnId, content, toolCalls, createdAt);
    }

    public List<ToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }
}
