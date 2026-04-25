package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 会话决策记录。
 */
@Getter
@Builder
@Jacksonized
public class DecisionRecord {

    /** 决策 ID。 */
    private final String decisionId;

    /** 决策内容。 */
    private final String content;

    /** 决策来源，USER / SYSTEM。 */
    private final String decidedBy;

    /** 决策时间。 */
    private final Instant decidedAt;

    /** 置信度。 */
    private final Double confidence;
}
