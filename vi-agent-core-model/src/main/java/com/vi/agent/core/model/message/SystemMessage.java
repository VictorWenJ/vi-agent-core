package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 系统提示消息。
 */
public final class SystemMessage extends AbstractMessage {

    private SystemMessage(
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
            MessageRole.SYSTEM,
            MessageType.SYSTEM_PROMPT,
            sequenceNo,
            status,
            contentText,
            createdAt
        );
    }

    public static SystemMessage create(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        String contentText
    ) {
        return new SystemMessage(
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

    public static SystemMessage restore(
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
        return new SystemMessage(
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
