package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * Transcript 消息领域接口。
 */
public interface Message {

    String getMessageId();

    String getConversationId();

    String getSessionId();

    String getTurnId();

    String getRunId();

    MessageRole getRole();

    MessageType getMessageType();

    long getSequenceNo();

    MessageStatus getStatus();

    String getContentText();

    Instant getCreatedAt();

    /**
     * 兼容旧链路读取。
     */
    default String getContent() {
        return getContentText();
    }
}
