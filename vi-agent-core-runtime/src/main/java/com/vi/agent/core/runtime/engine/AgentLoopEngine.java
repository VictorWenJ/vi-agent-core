package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;

/**
 * Agent Loop 执行引擎。
 */
public interface AgentLoopEngine {

    /**
     * 执行单次 Agent Loop。
     *
     * @param runContext 运行上下文
     * @return 助手消息
     */
    AssistantMessage run(AgentRunContext runContext);
}
