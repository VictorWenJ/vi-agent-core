package com.vi.agent.core.model.message;

import lombok.Getter;

import java.time.Instant;

/**
 * 消息基类。
 */
@Getter
public abstract class AbstractMessage implements Message {

    private final String messageId;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final MessageRole role;

    private final MessageType messageType;

    private final long sequenceNo;

    private final MessageStatus status;

    private final String contentText;

    private final Instant createdAt;

    protected AbstractMessage(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        MessageRole role,
        MessageType messageType,
        long sequenceNo,
        MessageStatus status,
        String contentText,
        Instant createdAt
    ) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.turnId = turnId;
        this.runId = runId;
        this.role = role;
        this.messageType = messageType;
        this.sequenceNo = sequenceNo;
        this.status = status == null ? MessageStatus.COMPLETED : status;
        this.contentText = contentText == null ? "" : contentText;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
