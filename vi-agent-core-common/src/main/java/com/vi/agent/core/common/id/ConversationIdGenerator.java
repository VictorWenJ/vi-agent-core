package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * conversationId 生成器。
 */
public class ConversationIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "conv-" + UUID.randomUUID();
    }
}
