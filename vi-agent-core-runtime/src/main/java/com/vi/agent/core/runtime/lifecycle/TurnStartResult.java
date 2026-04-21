package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.turn.Turn;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * turn 初始化结果。
 */
@Getter
@RequiredArgsConstructor
public class TurnStartResult {

    private final Turn turn;

    private final UserMessage userMessage;
}

