package com.vi.agent.core.model.message;

import lombok.Getter;

import java.time.Instant;

/**
 * Base message implementation.
 */
@Getter
public abstract class AbstractMessage implements Message {

    private final String messageId;

    private final String turnId;

    private final MessageRole role;

    private final MessageType messageType;

    private final long sequenceNo;

    private final String content;

    private final Instant createdAt;

    protected AbstractMessage(
        String messageId,
        String turnId,
        MessageRole role,
        MessageType messageType,
        long sequenceNo,
        String content,
        Instant createdAt
    ) {
        this.messageId = messageId;
        this.turnId = turnId;
        this.role = role;
        this.messageType = messageType;
        this.sequenceNo = sequenceNo;
        this.content = content == null ? "" : content;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
