package com.vi.agent.core.runtime.loop;

import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.LoopStreamObserverFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * loop 调用分发服务。
 */
@Service
public class LoopInvocationService {

    @Resource
    private AgentLoopEngine agentLoopEngine;

    @Resource
    private LoopStreamObserverFactory loopStreamObserverFactory;

    public LoopExecutionResult process(RuntimeExecutionContext context, RuntimeEventSink eventSink) {
        if (context.isStreaming()) {
            LoopStreamObserver streamObserver = loopStreamObserverFactory.create(eventSink);
            LoopExecutionResult loopExecutionResult = agentLoopEngine.runStreaming(context.getRunContext(), streamObserver);
            emitToolEvents(streamObserver, loopExecutionResult);
            return loopExecutionResult;
        }

        LoopExecutionResult loopExecutionResult = agentLoopEngine.run(context.getRunContext());
        emitToolEvents(eventSink, loopExecutionResult);
        return loopExecutionResult;
    }

    private void emitToolEvents(RuntimeEventSink eventSink, LoopExecutionResult loopExecutionResult) {
        if (loopExecutionResult == null) {
            return;
        }
        if (!CollectionUtils.isEmpty(loopExecutionResult.getToolCalls())) {
            loopExecutionResult.getToolCalls().forEach(eventSink::toolCall);
        }
        if (!CollectionUtils.isEmpty(loopExecutionResult.getToolExecutions())) {
            loopExecutionResult.getToolExecutions().forEach(eventSink::toolResult);
        }
    }

    private void emitToolEvents(LoopStreamObserver streamObserver, LoopExecutionResult loopExecutionResult) {
        if (loopExecutionResult == null) {
            return;
        }
        if (!CollectionUtils.isEmpty(loopExecutionResult.getToolCalls())) {
            loopExecutionResult.getToolCalls().forEach(streamObserver::onToolCall);
        }
        if (!CollectionUtils.isEmpty(loopExecutionResult.getToolExecutions())) {
            loopExecutionResult.getToolExecutions().forEach(streamObserver::onToolResult);
        }
    }
}
