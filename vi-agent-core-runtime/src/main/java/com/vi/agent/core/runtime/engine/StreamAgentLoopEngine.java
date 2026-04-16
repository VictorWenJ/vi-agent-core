package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;

import java.util.function.Consumer;

/**
 * 流式 Agent Loop 引擎接口。
 */
public interface StreamAgentLoopEngine {

    /**
     * 执行流式推理。
     *
     * @param runContext 运行上下文
     * @param chunkConsumer 分片消费器
     * @return 助手消息
     */
    AssistantMessage run(AgentRunContext runContext, Consumer<String> chunkConsumer);
}
