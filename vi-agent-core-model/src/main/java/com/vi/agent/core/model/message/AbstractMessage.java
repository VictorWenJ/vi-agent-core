package com.vi.agent.core.model.message;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 消息基础实现。
 */
@Getter
public abstract class AbstractMessage implements Message {

    /** 消息 ID。 */
    private final String messageId;

    /** 当前轮次 ID。 */
    private final String turnId;

    /** 消息角色。 */
    private final String role;

    /** 消息内容。 */
    private final String content;

    /** 消息创建时间。 */
    private final Instant createdAt;

    protected AbstractMessage(String messageId, String turnId, String role, String content, Instant createdAt) {
        this.messageId = (messageId == null || messageId.isBlank()) ? "msg-" + UUID.randomUUID() : messageId;
        this.turnId = turnId;
        this.role = role;
        this.content = content == null ? "" : content;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
