package com.vi.agent.core.model.port;

import com.vi.agent.core.model.session.SessionStateSnapshot;

import java.util.Optional;

/**
 * Session state cache repository port.
 */
public interface SessionStateRepository {

    Optional<SessionStateSnapshot> findBySessionId(String sessionId);

    void save(SessionStateSnapshot snapshot);

    void evict(String sessionId);
}
