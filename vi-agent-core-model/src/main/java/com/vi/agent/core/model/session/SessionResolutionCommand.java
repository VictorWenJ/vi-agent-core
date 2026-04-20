package com.vi.agent.core.model.session;

import lombok.Builder;
import lombok.Getter;

/**
 * Command used by session resolver.
 */
@Getter
@Builder
public class SessionResolutionCommand {

    private final String conversationId;

    private final String sessionId;

    private final SessionMode sessionMode;
}
