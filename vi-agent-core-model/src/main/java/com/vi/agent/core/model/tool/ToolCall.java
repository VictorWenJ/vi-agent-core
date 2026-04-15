package com.vi.agent.core.model.tool;

/**
 * 工具调用请求。
 */
public class ToolCall {

    /** 工具调用 ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 工具参数 JSON。 */
    private String argumentsJson;

    public ToolCall() {
    }

    public ToolCall(String toolCallId, String toolName, String argumentsJson) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
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

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }
}
