package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 消息基础实现。
 */
public abstract class BaseMessage implements Message {

    /** 消息角色。 */
    private final String role;

    /** 消息内容。 */
    private final String content;

    /** 消息创建时间。 */
    private final Instant createdAt;

    protected BaseMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
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
