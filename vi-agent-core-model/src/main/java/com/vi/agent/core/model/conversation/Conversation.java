package com.vi.agent.core.model.conversation;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Frontend conversation window.
 */
@Getter
@Builder
public class Conversation {

    private final String conversationId;

    private final String title;

    private ConversationStatus status;

    private String activeSessionId;

    private final Instant createdAt;

    private Instant updatedAt;

    private Instant lastMessageAt;

    public void activateSession(String sessionId) {
        this.activeSessionId = sessionId;
        this.status = ConversationStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void touchLastMessageAt(Instant time) {
        this.lastMessageAt = time;
        this.updatedAt = time;
    }

    public void close() {
        this.status = ConversationStatus.CLOSED;
        this.updatedAt = Instant.now();
    }
}
