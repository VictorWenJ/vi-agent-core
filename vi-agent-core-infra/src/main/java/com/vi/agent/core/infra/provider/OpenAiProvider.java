package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.tool.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI Provider 占位实现（保留扩展位）。
 */
@Slf4j
public class OpenAiProvider implements LlmProvider {

    @Override
    public AssistantMessage generate(AgentRunContext runContext) {
        log.info("OpenAiProvider placeholder generate, sessionId={}", runContext.getSessionId());
        if (runContext.getUserInput().toLowerCase().contains("time")) {
            ToolCall toolCall = ToolCall.builder()
                .toolCallId("tc-openai-placeholder")
                .toolName("get_time")
                .argumentsJson("{}")
                .turnId(runContext.getTurnId())
                .build();
            return new AssistantMessage("检测到时间查询，准备调用工具。", List.of(toolCall));
        }
        return new AssistantMessage("OpenAI 占位回复: " + runContext.getUserInput());
    }

    @Override
    public AssistantMessage generateStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer) {
        AssistantMessage result = generate(runContext);
        if (chunkConsumer != null) {
            chunkConsumer.accept(result.getContent());
        }
        return result;
    }
}
