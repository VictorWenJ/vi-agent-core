package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * Evidence ID 生成器。
 */
public class EvidenceIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "evd-" + UUID.randomUUID();
    }
}
