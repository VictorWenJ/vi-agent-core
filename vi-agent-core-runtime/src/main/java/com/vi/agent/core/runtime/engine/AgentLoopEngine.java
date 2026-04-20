package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;

import java.util.function.Consumer;

/**
 * Agent loop engine.
 */
public interface AgentLoopEngine {

    LoopExecutionResult run(AgentRunContext runContext);

    LoopExecutionResult runStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer);
}
