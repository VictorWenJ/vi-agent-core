package com.vi.agent.core.infra.persistence.cache.session.key;

import org.springframework.stereotype.Component;

/**
 * Redis key 构建器。
 */
@Component
public class SessionRedisKeyBuilder {

    private static final String SESSION_WORKING_SET_PREFIX = "agent:session:working-set:";

    private static final String SESSION_STATE_PREFIX = "agent:session:state:";

    private static final String SESSION_SUMMARY_PREFIX = "agent:session:summary:";

    private static final String REQUEST_CACHE_PREFIX = "agent:request:";

    private static final String SESSION_LOCK_PREFIX = "agent:session:lock:";

    /** 构建 Session Working Set snapshot cache key。 */
    public String sessionWorkingSetKey(String sessionId) {
        return SESSION_WORKING_SET_PREFIX + sessionId;
    }

    /** 构建 Session State snapshot cache key。 */
    public String sessionStateKey(String sessionId) {
        return SESSION_STATE_PREFIX + sessionId;
    }

    /** 构建 Session Summary snapshot cache key。 */
    public String sessionSummaryKey(String sessionId) {
        return SESSION_SUMMARY_PREFIX + sessionId;
    }

    /** 构建 request cache key。 */
    public String requestCacheKey(String requestId) {
        return REQUEST_CACHE_PREFIX + requestId;
    }

    /** 构建 session lock key。 */
    public String sessionLockKey(String sessionId) {
        return SESSION_LOCK_PREFIX + sessionId;
    }
}
