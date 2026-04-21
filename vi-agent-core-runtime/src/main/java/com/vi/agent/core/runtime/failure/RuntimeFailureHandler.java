package com.vi.agent.core.runtime.failure;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Runtime 失败收口处理器。
 */
@Service
public class RuntimeFailureHandler {

    @Resource
    private PersistenceCoordinator persistenceCoordinator;

    @Resource
    private TurnLifecycleService turnLifecycleService;

    public AgentRuntimeException handle(RuntimeExecutionContext context, Throwable throwable, RuntimeEventSink eventSink) {
        AgentRuntimeException runtimeException = toRuntimeException(throwable);
        String errorCode = runtimeException.getErrorCode().getCode();
        String errorMessage = resolveErrorMessage(throwable);

        if (context.hasRunContext()) {
            context.getRunContext().markFailed();
        }

        if (context.hasTurn()) {
            if (context.hasRunContext()) {
                persistenceCoordinator.persistFailure(context.getRunContext(), errorCode, errorMessage);
            } else {
                turnLifecycleService.failTurn(context.getTurn(), errorCode, errorMessage);
            }
        }

        eventSink.runFailed(errorCode, errorMessage, "SYSTEM", false);
        return runtimeException;
    }

    private AgentRuntimeException toRuntimeException(Throwable throwable) {
        if (throwable instanceof AgentRuntimeException runtimeException) {
            return runtimeException;
        }
        return new AgentRuntimeException(ErrorCode.RUNTIME_EXECUTION_FAILED, "runtime execution failed", throwable);
    }

    private String resolveErrorMessage(Throwable throwable) {
        if (throwable instanceof AgentRuntimeException runtimeException) {
            return runtimeException.getMessage();
        }
        return StringUtils.defaultIfBlank(
            throwable == null ? null : throwable.getMessage(),
            "runtime execution failed"
        );
    }
}

