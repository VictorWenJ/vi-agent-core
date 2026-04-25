package com.vi.agent.core.runtime.completion;

import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentExecutionResultFactory;
import com.vi.agent.core.runtime.memory.SessionMemoryCoordinator;
import com.vi.agent.core.runtime.memory.SessionMemoryUpdateCommand;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Runtime success completion handler.
 */
@Slf4j
@Service
public class RuntimeCompletionHandler {

    @Resource
    private PersistenceCoordinator persistenceCoordinator;

    @Resource
    private AgentExecutionResultFactory agentExecutionResultFactory;

    @Resource
    private SessionMemoryCoordinator sessionMemoryCoordinator;

    public AgentExecutionResult complete(RuntimeExecutionContext context, RuntimeEventSink eventSink) {
        LoopExecutionResult loopExecutionResult = context.getLoopResult();
        context.getRunContext().markCompleted();
        persistenceCoordinator.persistSuccess(context.getRunContext(), loopExecutionResult);
        updateSessionMemory(context, loopExecutionResult);
        eventSink.runCompleted(loopExecutionResult);
        return agentExecutionResultFactory.completed(context, loopExecutionResult);
    }

    private void updateSessionMemory(RuntimeExecutionContext context, LoopExecutionResult loopExecutionResult) {
        try {
            sessionMemoryCoordinator.updateAfterTurn(SessionMemoryUpdateCommand.builder()
                .conversationId(context.conversationId())
                .sessionId(context.sessionId())
                .turnId(context.turnId())
                .runId(context.runId())
                .traceId(context.getRunMetadata() == null ? null : context.getRunMetadata().getTraceId())
                .currentUserMessageId(context.getUserMessage() == null ? null : context.getUserMessage().getMessageId())
                .assistantMessageId(loopExecutionResult == null || loopExecutionResult.getAssistantMessage() == null
                    ? null : loopExecutionResult.getAssistantMessage().getMessageId())
                .workingContextSnapshotId(resolveWorkingContextSnapshotId(context))
                .agentMode(context.getRunContext().getAgentMode())
                .build());
        } catch (Exception ex) {
            log.warn("Post-turn session memory update failed and main chat result remains completed, sessionId={}, turnId={}",
                context.sessionId(), context.turnId(), ex);
        }
    }

    private String resolveWorkingContextSnapshotId(RuntimeExecutionContext context) {
        if (context == null || context.getRunContext() == null || context.getRunContext().getWorkingContextBuildResult() == null) {
            return null;
        }
        if (context.getRunContext().getWorkingContextBuildResult().getContext() == null
            || context.getRunContext().getWorkingContextBuildResult().getContext().getMetadata() == null) {
            return null;
        }
        return context.getRunContext()
            .getWorkingContextBuildResult()
            .getContext()
            .getMetadata()
            .getWorkingContextSnapshotId();
    }
}
