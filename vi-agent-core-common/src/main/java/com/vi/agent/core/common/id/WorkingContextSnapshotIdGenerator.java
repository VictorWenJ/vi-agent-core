package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * workingContextSnapshotId 生成器。
 */
public class WorkingContextSnapshotIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "wcs-" + UUID.randomUUID();
    }
}
