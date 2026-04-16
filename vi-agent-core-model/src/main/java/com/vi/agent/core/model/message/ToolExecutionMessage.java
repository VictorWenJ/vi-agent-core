package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 工具执行结果消息。
 */
public class ToolExecutionMessage extends BaseMessage {

    /** 工具调用 ID。 */
    private final String toolCallId;

    /** 工具名称。 */
    private final String toolName;

    /** 工具输出内容。 */
    private final String toolOutput;

    public ToolExecutionMessage(String toolCallId, String toolName, String toolOutput) {
        this(null, toolCallId, toolName, toolOutput, Instant.now());
    }

    public ToolExecutionMessage(
        String messageId,
        String toolCallId,
        String toolName,
        String toolOutput,
        Instant createdAt
    ) {
        super(messageId, "tool", toolOutput, createdAt);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.toolOutput = toolOutput;
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
