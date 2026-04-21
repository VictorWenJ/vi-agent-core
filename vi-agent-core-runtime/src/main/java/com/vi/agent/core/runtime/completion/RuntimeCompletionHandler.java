package com.vi.agent.core.runtime.completion;

import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentExecutionResultFactory;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Runtime 成功完成收口处理器。
 */
@Service
public class RuntimeCompletionHandler {

    @Resource
    private PersistenceCoordinator persistenceCoordinator;

    @Resource
    private AgentExecutionResultFactory agentExecutionResultFactory;

    public AgentExecutionResult complete(RuntimeExecutionContext context, RuntimeEventSink eventSink) {
        LoopExecutionResult loopExecutionResult = context.getLoopResult();
        context.getRunContext().markCompleted();
        persistenceCoordinator.persistSuccess(context.getRunContext(), loopExecutionResult);
        eventSink.runCompleted(loopExecutionResult);
        return agentExecutionResultFactory.completed(context, loopExecutionResult);
    }
}

