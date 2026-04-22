package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 用户输入消息。
 */
public final class UserMessage extends AbstractMessage {

    private UserMessage(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        MessageStatus status,
        String contentText,
        Instant createdAt
    ) {
        super(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            MessageRole.USER,
            MessageType.USER_INPUT,
            sequenceNo,
            status,
            contentText,
            createdAt
        );
    }

    public static UserMessage create(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        String contentText
    ) {
        return new UserMessage(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            sequenceNo,
            MessageStatus.COMPLETED,
            contentText,
            Instant.now()
        );
    }

    public static UserMessage restore(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        MessageStatus status,
        String contentText,
        Instant createdAt
    ) {
        return new UserMessage(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            sequenceNo,
            status,
            contentText,
            createdAt
        );
    }
}
