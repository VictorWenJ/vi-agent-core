package com.vi.agent.core.infra.persistence.cache.session.key;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionRedisKeyBuilderTest {

    private final SessionRedisKeyBuilder keyBuilder = new SessionRedisKeyBuilder();

    @Test
    void shouldBuildP2SnapshotKeysAndKeepRequestKey() {
        assertEquals("agent:session:working-set:sess-1", keyBuilder.sessionWorkingSetKey("sess-1"));
        assertEquals("agent:session:state:sess-1", keyBuilder.sessionStateKey("sess-1"));
        assertEquals("agent:session:summary:sess-1", keyBuilder.sessionSummaryKey("sess-1"));
        assertEquals("agent:request:req-1", keyBuilder.requestCacheKey("req-1"));
    }

    @Test
    void shouldKeepSessionLockKey() {
        assertEquals("agent:session:lock:sess-1", keyBuilder.sessionLockKey("sess-1"));
    }
}
