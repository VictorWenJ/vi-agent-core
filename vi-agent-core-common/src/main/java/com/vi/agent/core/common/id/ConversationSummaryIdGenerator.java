package com.vi.agent.core.common.id;

import java.util.UUID;

/**
 * 会话摘要 ID 生成器。
 */
public class ConversationSummaryIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "summary-" + UUID.randomUUID();
    }
}
