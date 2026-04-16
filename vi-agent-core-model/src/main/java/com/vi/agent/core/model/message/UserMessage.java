package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 用户消息。
 */
public class UserMessage extends BaseMessage {

    public UserMessage(String content) {
        super(null, "user", content);
    }

    public UserMessage(String messageId, String content) {
        super(messageId, "user", content);
    }

    public UserMessage(String messageId, String content, Instant createdAt) {
        super(messageId, "user", content, createdAt);
    }
}
