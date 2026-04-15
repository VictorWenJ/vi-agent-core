package com.vi.agent.core.model.tool;

/**
 * 工具执行结果。
 */
public class ToolResult {

    /** 工具调用 ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 是否执行成功。 */
    private boolean success;

    /** 工具输出内容。 */
    private String output;

    public ToolResult() {
    }

    public ToolResult(String toolCallId, String toolName, boolean success, String output) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.success = success;
        this.output = output;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}
