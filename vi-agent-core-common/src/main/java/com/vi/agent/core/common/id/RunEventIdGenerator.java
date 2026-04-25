package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * runEventId generator.
 */
public class RunEventIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "evt-" + UUID.randomUUID();
    }
}
