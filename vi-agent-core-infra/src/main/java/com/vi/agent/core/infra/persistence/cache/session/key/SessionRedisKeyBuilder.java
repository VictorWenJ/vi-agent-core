package com.vi.agent.core.infra.persistence.cache.session.key;

import org.springframework.stereotype.Component;

/**
 * Redis key builder for session cache and lock.
 */
@Component
public class SessionRedisKeyBuilder {

    private static final String SESSION_STATE_PREFIX = "agent:session:state:";

    private static final String SESSION_LOCK_PREFIX = "agent:session:lock:";

    public String sessionStateKey(String sessionId) {
        return SESSION_STATE_PREFIX + sessionId;
    }

    public String sessionLockKey(String sessionId) {
        return SESSION_LOCK_PREFIX + sessionId;
    }
}
