package com.vi.agent.core.model.port;

import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;

import java.util.Optional;

/**
 * Session working set cache repository port.
 */
public interface SessionWorkingSetRepository {

    SessionWorkingSetSnapshot findBySessionId(String sessionId);

    void save(SessionWorkingSetSnapshot snapshot);

    void evict(String sessionId);
}

