package com.vi.agent.core.runtime.scope;

import com.vi.agent.core.model.port.SessionLockRepository;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.mdc.MdcScope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 单次运行资源作用域，负责释放 lock、MDC、sequence cursor。
 */
@Slf4j
public class RuntimeRunScope implements AutoCloseable {

    private final String sessionId;

    private final String runId;

    private final MdcScope mdcScope;

    private final SessionLockRepository sessionLockRepository;

    private final MessageFactory messageFactory;

    public RuntimeRunScope(
        String sessionId,
        String runId,
        MdcScope mdcScope,
        SessionLockRepository sessionLockRepository,
        MessageFactory messageFactory
    ) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.mdcScope = mdcScope;
        this.sessionLockRepository = sessionLockRepository;
        this.messageFactory = messageFactory;
    }

    @Override
    public void close() {
        try {
            if (mdcScope != null) {
                mdcScope.close();
            }
        } catch (Exception e) {
            log.error("RuntimeRunScope close mdc failed, sessionId={}, runId={}", sessionId, runId, e);
        }

        try {
            if (StringUtils.isNotBlank(sessionId) && StringUtils.isNotBlank(runId)) {
                sessionLockRepository.unlock(sessionId, runId);
            }
        } catch (Exception e) {
            log.error("RuntimeRunScope unlock failed, sessionId={}, runId={}", sessionId, runId, e);
        }

        try {
            if (StringUtils.isNotBlank(sessionId)) {
                messageFactory.clearSessionSequenceCursor(sessionId);
            }
        } catch (Exception e) {
            log.error("RuntimeRunScope clear sequence cursor failed, sessionId={}", sessionId, e);
        }
    }
}

