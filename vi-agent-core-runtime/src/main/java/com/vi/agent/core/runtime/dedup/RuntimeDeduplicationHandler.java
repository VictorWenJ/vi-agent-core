package com.vi.agent.core.runtime.dedup;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentExecutionResultFactory;
import com.vi.agent.core.runtime.factory.RuntimeEventFactory;
import com.vi.agent.core.runtime.lifecycle.TurnDedupResult;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

        Turn turn = turnDedupResult.getTurn();

        RuntimeExecuteCommand command = context.getCommand();
        return switch (turnDedupResult.getStatus()) {
            case COMPLETED ->
                agentExecutionResultFactory.completedFromTurn(command, turn, turnDedupResult.getAssistantMessage());
            case RUNNING -> {
                emitIfPresent(context, runtimeEventFactory.processing(command, turn));
                yield agentExecutionResultFactory.processing(command, turn);
            }
            case FAILED -> {
                emitIfPresent(context, runtimeEventFactory.runFailed(
                    context,
                    StringUtils.defaultIfBlank(turn.getErrorCode(), "TURN_FAILED"),
                    StringUtils.defaultIfBlank(turn.getErrorMessage(), "turn already failed"),
                    "BUSINESS",
                    false
                ));
                yield agentExecutionResultFactory.failedFromTurn(command, turn);
            }
            case CANCELLED -> agentExecutionResultFactory.cancelledFromTurn(command, turn);
        };
    }

    private void emitIfPresent(RuntimeExecutionContext context, RuntimeEvent runtimeEvent) {
        if (context.getEventConsumer() == null || runtimeEvent == null) {
            return;
        }
        context.getEventConsumer().accept(runtimeEvent);
    }
}
