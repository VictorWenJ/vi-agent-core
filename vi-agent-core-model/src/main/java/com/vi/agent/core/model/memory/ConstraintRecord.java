package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * 会话约束记录。
 */
@Getter
@Builder
public class ConstraintRecord {

    /** 约束 ID。 */
    private final String constraintId;

    /** 约束适用范围。 */
    private final String scope;

    /** 约束内容。 */
    private final String content;

    /** 约束是否仍然有效。 */
    private final Boolean active;

    /** 支撑该约束的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;

    /** 约束创建时间。 */
    private final Instant createdAt;

    /** 约束更新时间。 */
    private final Instant updatedAt;
}
