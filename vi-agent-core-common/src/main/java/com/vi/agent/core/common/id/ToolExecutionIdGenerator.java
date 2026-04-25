package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * toolExecutionId generator.
 */
public class ToolExecutionIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "tex-" + UUID.randomUUID();
    }
}
