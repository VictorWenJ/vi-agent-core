package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.runtime.AgentRunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Phase 1 默认 Agent Loop 引擎。
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleAgentLoopEngine implements AgentLoopEngine {

    /** 模型调用网关。 */
    private final LlmGateway llmGateway;

    @Override
    public AssistantMessage run(AgentRunContext runContext) {
        return llmGateway.generate(runContext);
    }

    @Override
    public AssistantMessage runStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer) {
        return llmGateway.generateStreaming(runContext, chunkConsumer);
    }
}
