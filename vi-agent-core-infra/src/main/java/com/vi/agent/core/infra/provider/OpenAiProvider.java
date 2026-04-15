package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.tool.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * OpenAI Provider Phase 1 骨架实现。
 */
@Slf4j
public class OpenAiProvider implements LlmProvider {

    @Override
    public AssistantMessage generate(AgentRunContext runContext) {
        log.debug("OpenAiProvider generate start");
        if (runContext.getUserInput().toLowerCase().contains("time")) {
            log.info("OpenAiProvider detected time query, prepare tool call");
            ToolCall toolCall = new ToolCall("tool-call-time", "get_time", "{}");
            return new AssistantMessage("检测到时间查询，准备调用工具。", List.of(toolCall));
        }

        log.debug("OpenAiProvider return placeholder response");
        return new AssistantMessage("Phase 1 占位回复: " + runContext.getUserInput());
    }
}
