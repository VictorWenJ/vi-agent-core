package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 已确认事实记录。
 */
@Getter
@Builder
@Jacksonized
public class ConfirmedFactRecord {

    /** 事实 ID。 */
    private final String factId;

    /** 事实内容。 */
    private final String content;

    /** 事实置信度。 */
    private final Double confidence;

    /** 最后验证时间。 */
    private final Instant lastVerifiedAt;

    /** 过期策略。 */
    private final StalePolicy stalePolicy;
}
