package com.vi.agent.core.model.port;

import com.vi.agent.core.model.turn.Turn;

import java.util.Optional;

/**
 * Turn repository port.
 */
public interface TurnRepository {

    Optional<Turn> findByRequestId(String requestId);

    Optional<Turn> findByTurnId(String turnId);

    boolean existsRunningBySessionId(String sessionId);

    void save(Turn turn);

    void update(Turn turn);
}
