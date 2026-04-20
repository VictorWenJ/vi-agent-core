package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;

import java.util.List;

/**
 * Tool gateway.
 */
public interface ToolGateway {

    ToolResult execute(ToolCall toolCall);

    List<ToolDefinition> listDefinitions();
}
