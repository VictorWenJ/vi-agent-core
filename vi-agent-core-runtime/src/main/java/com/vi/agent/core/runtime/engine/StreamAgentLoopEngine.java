package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.runtime.AgentRunContext;

import java.util.List;

/**
 * 流式 Agent Loop 引擎接口（Phase 1 预留）。
 */
public interface StreamAgentLoopEngine {

    /**
     * 执行流式输出。
     *
     * @param runContext 运行上下文
     * @return 分段输出列表
     */
    List<String> stream(AgentRunContext runContext);
}
