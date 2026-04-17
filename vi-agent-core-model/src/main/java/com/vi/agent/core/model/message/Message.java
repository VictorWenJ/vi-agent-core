package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * 运行时消息抽象。
 */
public interface Message {

    /**
     * 获取消息 ID。
     *
     * @return 消息 ID
     */
    String getMessageId();

    /**
     * 获取消息所属轮次 ID。
     *
     * @return 轮次 ID
     */
    String getTurnId();

    /**
     * 获取消息角色。
     *
     * @return 角色标识
     */
    String getRole();

    /**
     * 获取消息内容。
     *
     * @return 消息内容
     */
    String getContent();

    /**
     * 获取消息创建时间。
     *
     * @return 创建时间
     */
    Instant getCreatedAt();
}
