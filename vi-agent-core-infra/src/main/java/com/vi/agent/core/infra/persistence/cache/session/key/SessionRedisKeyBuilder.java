package com.vi.agent.core.infra.persistence.cache.session.key;

import org.springframework.stereotype.Component;

/**
 * Redis key 构建器。
 */
@Component
public class SessionRedisKeyBuilder {

    private static final String SESSION_WORKING_SET_PREFIX = "agent:session:working-set:";

    private static final String REQUEST_CACHE_PREFIX = "agent:request:";

    private static final String SESSION_LOCK_PREFIX = "agent:session:lock:";

    public String sessionWorkingSetKey(String sessionId) {
        return SESSION_WORKING_SET_PREFIX + sessionId;
    }

    public String requestCacheKey(String requestId) {
        return REQUEST_CACHE_PREFIX + requestId;
    }

    public String sessionLockKey(String sessionId) {
        return SESSION_LOCK_PREFIX + sessionId;
    }
}