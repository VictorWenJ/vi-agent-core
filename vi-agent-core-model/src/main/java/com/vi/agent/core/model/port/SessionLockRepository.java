package com.vi.agent.core.model.port;

import java.time.Duration;

/**
 * Session lock repository port.
 */
public interface SessionLockRepository {

    boolean tryLock(String sessionId, String runId, Duration ttl);

    void unlock(String sessionId, String runId);
}
