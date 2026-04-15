package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.runtime.port.LlmGateway;

/**
 * Phase 1 默认 Agent Loop 引擎。
 */
public class DefaultAgentLoopEngine implements AgentLoopEngine {

    /** 模型调用网关。 */
    private final LlmGateway llmGateway;

    public DefaultAgentLoopEngine(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    @Override
    public AssistantMessage run(AgentRunContext runContext) {
        return llmGateway.generate(runContext);
    }
}
