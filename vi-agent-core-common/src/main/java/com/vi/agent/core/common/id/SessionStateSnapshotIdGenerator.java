package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * session state snapshot id generator.
 */
public class SessionStateSnapshotIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "state-" + UUID.randomUUID();
    }
}
