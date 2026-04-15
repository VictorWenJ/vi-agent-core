package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.tool.ToolCall;

import java.util.List;

/**
 * OpenAI Provider Phase 1 骨架实现。
 */
public class OpenAiProvider implements LlmProvider {

    @Override
    public AssistantMessage generate(AgentRunContext runContext) {
        if (runContext.getUserInput().toLowerCase().contains("time")) {
            ToolCall toolCall = new ToolCall("tool-call-time", "get_time", "{}");
            return new AssistantMessage("检测到时间查询，准备调用工具。", List.of(toolCall));
        }

        return new AssistantMessage("Phase 1 占位回复: " + runContext.getUserInput());
    }
}
