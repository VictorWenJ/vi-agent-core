package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default tool gateway.
 */
@Slf4j
@Component
public class DefaultToolGateway implements ToolGateway {

    @Resource
    private ToolRegistry toolRegistry;

    @Override
    public ToolResult execute(ToolCall toolCall) {
        log.info("DefaultToolGateway execute toolCall={}", JsonUtils.toJson(toolCall));
        ToolExecutor toolExecutor = toolRegistry.find(toolCall.getToolName())
            .orElseThrow(() -> new AgentRuntimeException(
                ErrorCode.TOOL_NOT_FOUND,
                "Tool not found: " + toolCall.getToolName()
            ));

        ToolResult toolResult = toolExecutor.execute(toolCall);
        if (toolResult == null) {
            throw new AgentRuntimeException(ErrorCode.TOOL_EXECUTION_FAILED, "Tool returned null: " + toolCall.getToolName());
        }
        log.info("DefaultToolGateway execute toolResult={}", JsonUtils.toJson(toolResult));

        return toolResult;
    }

    @Override
    public List<ToolDefinition> listDefinitions() {
        return toolRegistry.listDefinitions();
    }
}
