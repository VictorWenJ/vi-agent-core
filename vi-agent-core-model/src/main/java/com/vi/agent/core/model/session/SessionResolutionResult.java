package com.vi.agent.core.model.session;

import com.vi.agent.core.model.conversation.Conversation;
import lombok.Builder;
import lombok.Getter;

/**
 * Session resolution result.
 */
@Getter
@Builder
public class SessionResolutionResult {

    private final Conversation conversation;

    private final Session session;

    private final boolean createdConversation;

    private final boolean createdSession;
}
