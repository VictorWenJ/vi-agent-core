package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * turnId 生成器。
 */
public class TurnIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "turn-" + UUID.randomUUID();
    }
}
