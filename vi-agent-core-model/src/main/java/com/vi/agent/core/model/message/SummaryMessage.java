package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 摘要上下文消息。
 */
public final class SummaryMessage extends AbstractMessage {

    private SummaryMessage(
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
            MessageRole.SUMMARY,
            MessageType.SUMMARY_CONTEXT,
            sequenceNo,
            status,
            contentText,
            createdAt
        );
    }

    public static SummaryMessage create(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        String contentText
    ) {
        return new SummaryMessage(
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

    public static SummaryMessage restore(
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
        return new SummaryMessage(
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
