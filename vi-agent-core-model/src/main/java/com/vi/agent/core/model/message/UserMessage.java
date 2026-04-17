package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 用户消息。
 */
public class UserMessage extends AbstractMessage {

    private UserMessage(String messageId, String turnId, String content, Instant createdAt) {
        super(messageId, turnId, "user", content, createdAt);
    }

    /**
     * 运行时新建用户消息。
     *
     * @param messageId 消息 ID（可为空，为空时自动生成）
     * @param turnId 轮次 ID
     * @param content 消息内容
     * @return 用户消息
     */
    public static UserMessage create(String messageId, String turnId, String content) {
        return new UserMessage(messageId, turnId, content, Instant.now());
    }

    /**
     * 持久化恢复用户消息。
     *
     * @param messageId 消息 ID
     * @param turnId 轮次 ID
     * @param content 消息内容
     * @param createdAt 创建时间
     * @return 用户消息
     */
    public static UserMessage restore(String messageId, String turnId, String content, Instant createdAt) {
        return new UserMessage(messageId, turnId, content, createdAt);
    }
}
