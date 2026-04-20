package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.turn.Turn;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of turn initialization or request dedup hit.
 */
@Getter
@Builder
public class TurnDedupResult {

    private final TurnReuseStatus status;

    private final Turn turn;

    private final Message userMessage;

    private final Message assistantMessage;
}
