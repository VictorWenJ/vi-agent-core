package com.vi.agent.core.model.port;

import com.vi.agent.core.model.message.Message;

import java.util.List;

/**
 * Message 持久化端口。
 */
public interface MessageRepository {

    /**
     * 批量保存消息聚合事实。
     */
    void saveBatch(List<Message> messages);

    /**
     * 兼容单条保存。
     */
    default void save(Message message) {
        saveBatch(List.of(message));
    }

    Message findByMessageId(String messageId);

    List<Message> findCompletedContextBySessionId(String sessionId, int maxMessages);

    List<Message> findByTurnId(String turnId);

    Message findFinalAssistantMessageByTurnId(String turnId);

    long nextSequenceNo(String sessionId);
}
