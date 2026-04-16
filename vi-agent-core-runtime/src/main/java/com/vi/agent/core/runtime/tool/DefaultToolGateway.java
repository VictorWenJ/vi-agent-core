package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 默认工具网关实现。
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultToolGateway implements ToolGateway {

    /** 工具注册表。 */
    private final ToolRegistry toolRegistry;

    @Override
    public ToolResult route(ToolCall toolCall) {
        ToolExecutor toolExecutor = toolRegistry.find(toolCall.getToolName())
            .orElseThrow(() -> new AgentRuntimeException(
                ErrorCode.TOOL_NOT_FOUND,
                "未找到工具: " + toolCall.getToolName()
            ));

        ToolResult result = toolExecutor.execute(toolCall);
        if (result == null) {
            throw new AgentRuntimeException(
                ErrorCode.TOOL_EXECUTION_FAILED,
                "工具返回空结果: " + toolCall.getToolName()
            );
        }

        log.info("ToolGateway route done toolName={} toolCallId={} success={}",
            result.getToolName(), result.getToolCallId(), result.isSuccess());
        return result;
    }

    @Override
    public List<ToolDefinition> listDefinitions() {
        return toolRegistry.listDefinitions();
    }
}
