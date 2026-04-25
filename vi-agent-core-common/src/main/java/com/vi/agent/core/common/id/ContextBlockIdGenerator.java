package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * context block id generator.
 */
public class ContextBlockIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "ctxblk-" + UUID.randomUUID();
    }
}
