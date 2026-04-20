package com.vi.agent.core.runtime.session;

import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.model.session.SessionResolutionResult;

/**
 * Resolves conversation/session by session mode.
 */
public interface SessionResolutionService {

    SessionResolutionResult resolve(RuntimeExecuteCommand command);
}
