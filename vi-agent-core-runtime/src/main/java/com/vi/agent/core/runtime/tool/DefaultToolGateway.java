package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        log.debug("ToolGateway route start toolName={} toolCallId={}",
            toolCall.getToolName(), toolCall.getToolCallId());

        ToolExecutor toolExecutor = toolRegistry.find(toolCall.getToolName())
            .orElseThrow(() -> {
                log.warn("ToolGateway route failed, tool not found toolName={} toolCallId={}",
                    toolCall.getToolName(), toolCall.getToolCallId());
                return new AgentRuntimeException(
                ErrorCode.TOOL_NOT_FOUND,
                "未找到工具: " + toolCall.getToolName()
                );
            });
        ToolResult result = toolExecutor.execute(toolCall);
        log.debug("ToolGateway route success toolName={} toolCallId={} success={}",
            result.getToolName(), result.getToolCallId(), result.isSuccess());
        return result;
    }
}
