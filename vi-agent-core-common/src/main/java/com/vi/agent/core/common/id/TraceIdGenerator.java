package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * traceId 生成器。
 */
public class TraceIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "trace-" + UUID.randomUUID();
    }
}
