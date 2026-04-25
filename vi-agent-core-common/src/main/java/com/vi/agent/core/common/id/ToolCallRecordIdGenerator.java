package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * toolCallRecordId generator.
 */
public class ToolCallRecordIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "tcr-" + UUID.randomUUID();
    }
}
