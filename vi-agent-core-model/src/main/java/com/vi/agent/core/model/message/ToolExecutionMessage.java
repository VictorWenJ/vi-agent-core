package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 工具执行结果消息。
 */
public class ToolExecutionMessage extends AbstractMessage {

    /** 工具调用 ID。 */
    private final String toolCallId;

    /** 工具名称。 */
    private final String toolName;

    /** 工具输出内容。 */
    private final String toolOutput;

    private ToolExecutionMessage(
        String messageId,
        String turnId,
        String toolCallId,
        String toolName,
        String toolOutput,
        Instant createdAt
    ) {
        super(messageId, turnId, "tool", toolOutput, createdAt);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.toolOutput = toolOutput;
    }

    /**
     * 运行时新建工具执行消息。
     *
     * @param messageId 消息 ID（可为空，为空时自动生成）
     * @param turnId 轮次 ID
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param toolOutput 工具输出
     * @return 工具执行消息
     */
    public static ToolExecutionMessage create(
        String messageId,
        String turnId,
        String toolCallId,
        String toolName,
        String toolOutput
    ) {
        return new ToolExecutionMessage(messageId, turnId, toolCallId, toolName, toolOutput, Instant.now());
    }

    /**
     * 持久化恢复工具执行消息。
     *
     * @param messageId 消息 ID
     * @param turnId 轮次 ID
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param toolOutput 工具输出
     * @param createdAt 创建时间
     * @return 工具执行消息
     */
    public static ToolExecutionMessage restore(
        String messageId,
        String turnId,
        String toolCallId,
        String toolName,
        String toolOutput,
        Instant createdAt
    ) {
        return new ToolExecutionMessage(messageId, turnId, toolCallId, toolName, toolOutput, createdAt);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolOutput() {
        return toolOutput;
    }
}
