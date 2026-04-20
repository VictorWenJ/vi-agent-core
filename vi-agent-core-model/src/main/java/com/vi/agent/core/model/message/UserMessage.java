package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * User message.
 */
public final class UserMessage extends AbstractMessage {

    private UserMessage(String messageId, String turnId, long sequenceNo, String content, Instant createdAt) {
        super(messageId, turnId, MessageRole.USER, MessageType.USER_INPUT, sequenceNo, content, createdAt);
    }

    public static UserMessage create(String messageId, String turnId, long sequenceNo, String content) {
        return new UserMessage(messageId, turnId, sequenceNo, content, Instant.now());
    }

    public static UserMessage restore(String messageId, String turnId, long sequenceNo, String content, Instant createdAt) {
        return new UserMessage(messageId, turnId, sequenceNo, content, createdAt);
    }
}
