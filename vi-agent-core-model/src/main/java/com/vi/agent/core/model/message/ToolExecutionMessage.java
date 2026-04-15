package com.vi.agent.core.model.message;

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
        super("tool", toolOutput);
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
