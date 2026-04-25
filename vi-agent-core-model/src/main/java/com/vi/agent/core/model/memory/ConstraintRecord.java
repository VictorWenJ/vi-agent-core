package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 会话约束记录。
 */
@Getter
@Builder
@Jacksonized
public class ConstraintRecord {

    /** 约束 ID。 */
    private final String constraintId;

    /** 约束内容。 */
    private final String content;

    /** 约束作用域。 */
    private final ConstraintScope scope;

    /** 约束置信度。 */
    private final Double confidence;

    /** 最后验证时间。 */
    private final Instant lastVerifiedAt;
}
