package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * internal task id generator.
 */
public class InternalTaskIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "itask-" + UUID.randomUUID();
    }
}
