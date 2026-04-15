package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * runId 生成器。
 */
public class RunIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "run-" + UUID.randomUUID();
    }
}
