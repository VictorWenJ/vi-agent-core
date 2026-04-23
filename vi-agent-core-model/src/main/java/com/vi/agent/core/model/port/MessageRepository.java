package com.vi.agent.core.model.port;

import com.vi.agent.core.model.message.Message;

import java.util.List;

/**
 * Message persistence port.
 */
public interface MessageRepository {

    void saveBatch(List<Message> messages);

    default void save(Message message) {
        saveBatch(List.of(message));
    }

    Message findByMessageId(String messageId);

    List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns);

    List<Message> findByTurnId(String turnId);

    Message findFinalAssistantMessageByTurnId(String turnId);

    long nextSequenceNo(String sessionId);
}