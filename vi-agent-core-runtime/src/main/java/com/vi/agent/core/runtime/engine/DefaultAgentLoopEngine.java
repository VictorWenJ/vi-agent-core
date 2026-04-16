package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.runtime.port.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Phase 1 默认 Agent Loop 引擎。
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultAgentLoopEngine implements AgentLoopEngine {

    /** 模型调用网关。 */
    private final LlmGateway llmGateway;

    @Override
    public AssistantMessage run(AgentRunContext runContext) {
        log.info("DefaultAgentLoopEngine run iteration={} turnId={}", runContext.getIteration(), runContext.getTurnId());
        return llmGateway.generate(runContext);
    }

    @Override
    public AssistantMessage runStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer) {
        log.info("DefaultAgentLoopEngine runStreaming iteration={} turnId={}", runContext.getIteration(), runContext.getTurnId());
        return llmGateway.generateStreaming(runContext, chunkConsumer);
    }
}
