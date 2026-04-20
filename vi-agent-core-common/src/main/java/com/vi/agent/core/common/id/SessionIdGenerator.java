package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * conversationId 生成器。
 */
public class SessionIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "Sess-" + UUID.randomUUID();
    }
}
