package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * working context projection id generator.
 */
public class WorkingContextProjectionIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "wcp-" + UUID.randomUUID();
    }
}
