package com.vi.agent.core.model.port;

import com.vi.agent.core.model.message.Message;

import java.util.List;
import java.util.Optional;

/**
 * Message repository port.
 */
public interface MessageRepository {

    void save(String conversationId, String sessionId, Message message);

    Optional<Message> findByMessageId(String messageId);

    List<Message> findBySessionIdOrderBySequence(String sessionId, int limit);

    long nextSequenceNo(String sessionId);
}
