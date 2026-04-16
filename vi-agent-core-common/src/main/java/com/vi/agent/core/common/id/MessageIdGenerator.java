package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * messageId 生成器。
 */
public class MessageIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "msg-" + UUID.randomUUID();
    }
}
