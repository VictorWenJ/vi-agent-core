package com.vi.agent.core.model.port;

import com.vi.agent.core.model.session.Session;

import java.util.Optional;

/**
 * Session repository port.
 */
public interface SessionRepository {

    Optional<Session> findBySessionId(String sessionId);

    Optional<Session> findActiveByConversationId(String conversationId);

    void save(Session session);

    void update(Session session);
}
