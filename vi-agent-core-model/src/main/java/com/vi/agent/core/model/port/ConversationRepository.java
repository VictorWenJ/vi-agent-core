package com.vi.agent.core.model.port;

import com.vi.agent.core.model.conversation.Conversation;

import java.util.Optional;

/**
 * Conversation repository port.
 */
public interface ConversationRepository {

    Optional<Conversation> findByConversationId(String conversationId);

    void save(Conversation conversation);

    void update(Conversation conversation);
}
