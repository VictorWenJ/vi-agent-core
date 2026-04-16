package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;

import java.util.List;

/**
 * 工具网关统一入口。
 */
public interface ToolGateway {

    /**
     * 路由并执行工具调用。
     *
     * @param toolCall 工具调用请求
     * @return 工具执行结果
     */
    ToolResult route(ToolCall toolCall);

    /**
     * 获取当前已注册工具定义。
     *
     * @return 工具定义列表
     */
    List<ToolDefinition> listDefinitions();
}
