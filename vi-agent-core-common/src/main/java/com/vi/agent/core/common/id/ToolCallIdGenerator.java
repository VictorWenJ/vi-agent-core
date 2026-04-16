package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * toolCallId 生成器。
 */
public class ToolCallIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "tc-" + UUID.randomUUID();
    }
}
