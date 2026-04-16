package com.vi.agent.core.model.message;

import java.time.Instant;
import java.util.UUID;

/**
 * 消息基础实现。
 */
public abstract class BaseMessage implements Message {

    /** 消息 ID。 */
    private final String messageId;

    /** 消息角色。 */
    private final String role;

    /** 消息内容。 */
    private final String content;

    /** 消息创建时间。 */
    private final Instant createdAt;

    protected BaseMessage(String messageId, String role, String content) {
        this(messageId, role, content, Instant.now());
    }

    protected BaseMessage(String messageId, String role, String content, Instant createdAt) {
        this.messageId = (messageId == null || messageId.isBlank()) ? "msg-" + UUID.randomUUID() : messageId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }
}
