package com.vi.agent.core.model.session;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Runtime session under a conversation.
 */
@Getter
@Builder
public class Session {

    private final String sessionId;

    private final String conversationId;

    private final String parentSessionId;

    private SessionStatus status;

    private String archiveReason;

    private final Instant createdAt;

    private Instant updatedAt;

    private Instant archivedAt;

    public void archive(String reason, Instant archivedTime) {
        this.status = SessionStatus.ARCHIVED;
        this.archiveReason = reason;
        this.archivedAt = archivedTime;
        this.updatedAt = archivedTime;
    }

    public void markFailed() {
        this.status = SessionStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void touch(Instant time) {
        this.updatedAt = time;
    }
}
