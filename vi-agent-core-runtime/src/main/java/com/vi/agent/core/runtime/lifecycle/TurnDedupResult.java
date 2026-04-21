package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of turn initialization or request dedup hit.
 */
@Getter
@Builder
public class TurnDedupResult {

    private final TurnStatus status;

    private final Turn turn;

    private final Message userMessage;

    private final Message assistantMessage;
}
