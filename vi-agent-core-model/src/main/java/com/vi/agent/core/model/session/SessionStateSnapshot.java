package com.vi.agent.core.model.session;

import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Cached session state snapshot.
 */
@Getter
@Builder
public class SessionStateSnapshot {

    private final String sessionId;

    private final String conversationId;

    private final List<Message> messages;

    private final Instant updatedAt;
}
