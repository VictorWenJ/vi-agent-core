package com.vi.agent.core.runtime.scope;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.port.SessionLockRepository;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.mdc.MdcScope;
import com.vi.agent.core.runtime.mdc.RuntimeMdcManager;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Runtime 运行资源作用域管理器。
 */
@Service
public class RuntimeRunScopeManager {

    @Value("${vi.agent.redis.ttl.session-lock-seconds:60}")
    private long sessionLockTtlSeconds;

    @Resource
    private SessionLockRepository sessionLockRepository;

    @Resource
    private TurnLifecycleService turnLifecycleService;

    @Resource
    private RuntimeMdcManager runtimeMdcManager;

    @Resource
    private MessageFactory messageFactory;

    public RuntimeRunScope open(RuntimeExecutionContext context) {
        String sessionId = context.sessionId();
        String runId = context.runId();
        Duration lockTtl = Duration.ofSeconds(Math.max(1L, sessionLockTtlSeconds));

        if (!sessionLockRepository.tryLock(sessionId, runId, lockTtl)) {
            throw new AgentRuntimeException(ErrorCode.SESSION_CONCURRENT_REQUEST, "session has another running request");
        }

        boolean lockAcquired = true;
        try {
            if (turnLifecycleService.existsRunningTurn(sessionId)) {
                sessionLockRepository.unlock(sessionId, runId);
                lockAcquired = false;
                throw new AgentRuntimeException(ErrorCode.SESSION_CONCURRENT_REQUEST, "session has another running request");
            }

            MdcScope mdcScope = runtimeMdcManager.open(
                context.requestId(),
                context.conversationId(),
                sessionId,
                context.getRunMetadata()
            );
            return new RuntimeRunScope(sessionId, runId, mdcScope, sessionLockRepository, messageFactory);
        } catch (RuntimeException ex) {
            if (lockAcquired) {
                sessionLockRepository.unlock(sessionId, runId);
            }
            messageFactory.clearSessionSequenceCursor(sessionId);
            throw ex;
        }
    }
}
