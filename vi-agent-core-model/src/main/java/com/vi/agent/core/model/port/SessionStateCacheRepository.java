package com.vi.agent.core.model.port;

import com.vi.agent.core.model.memory.SessionStateSnapshot;

import java.util.Optional;

/**
 * Session State snapshot cache 仓储端口。
 */
public interface SessionStateCacheRepository {

    /** 按 session ID 读取缓存中的 state snapshot。 */
    Optional<SessionStateSnapshot> findBySessionId(String sessionId);

    /** 写入缓存中的 state snapshot。 */
    void save(SessionStateSnapshot snapshot);

    /** 删除缓存中的 state snapshot。 */
    void evict(String sessionId);
}
