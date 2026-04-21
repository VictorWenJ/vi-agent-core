package com.vi.agent.core.runtime.dedup;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentExecutionResultFactory;
import com.vi.agent.core.runtime.factory.RuntimeEventFactory;
import com.vi.agent.core.runtime.lifecycle.TurnDedupResult;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * requestId 幂等处理器。
 */
@Slf4j
@Service
public class RuntimeDeduplicationHandler {

    @Resource
    private TurnLifecycleService turnLifecycleService;

    @Resource
    private AgentExecutionResultFactory agentExecutionResultFactory;

    @Resource
    private RuntimeEventFactory runtimeEventFactory;

    public AgentExecutionResult checkAndBuildDedupAgentExecution(RuntimeExecutionContext context) {
        TurnDedupResult turnDedupResult = turnLifecycleService.findAndBuildByRequestId(context.requestId());
        log.info("RuntimeOrchestrator executeInternal turnDedupResult:{}", JsonUtils.toJson(turnDedupResult));
        if (turnDedupResult == null) {
            return null;
        }

        RuntimeExecuteCommand command = context.getCommand();
        return switch (turnDedupResult.getStatus()) {
            case COMPLETED ->
                agentExecutionResultFactory.completedFromTurn(command, turnDedupResult.getTurn(), turnDedupResult.getAssistantMessage());
            case RUNNING -> {
                context.getEventConsumer().accept(runtimeEventFactory.processing(command, turnDedupResult.getTurn()));
                yield agentExecutionResultFactory.processing(command, turnDedupResult.getTurn());
            }
            case FAILED -> agentExecutionResultFactory.failedFromTurn(command, turnDedupResult.getTurn());
            case CANCELLED -> agentExecutionResultFactory.cancelledFromTurn(command, turnDedupResult.getTurn());
        };
    }
}

