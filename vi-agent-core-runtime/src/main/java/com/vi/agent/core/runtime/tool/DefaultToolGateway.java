package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;

/**
 * 默认工具网关实现。
 */
public class DefaultToolGateway implements ToolGateway {

    /** 工具注册表。 */
    private final ToolRegistry toolRegistry;

    public DefaultToolGateway(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public ToolResult route(ToolCall toolCall) {
        return toolRegistry.find(toolCall.getToolName())
            .orElseThrow(() -> new AgentRuntimeException(
                ErrorCode.TOOL_NOT_FOUND,
                "未找到工具: " + toolCall.getToolName()
            ))
            .execute(toolCall);
    }
}
