package com.vi.agent.core.runtime.port;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;

/**
 * 运行时模型调用网关。
 */
public interface LlmGateway {

    /**
     * 生成助手回复。
     *
     * @param runContext 运行上下文
     * @return 助手消息
     */
    AssistantMessage generate(AgentRunContext runContext);
}
