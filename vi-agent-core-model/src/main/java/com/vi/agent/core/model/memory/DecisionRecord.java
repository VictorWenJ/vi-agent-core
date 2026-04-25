package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * 会话决策记录。
 */
@Getter
@Builder
public class DecisionRecord {

    /** 决策 ID。 */
    private final String decisionId;

    /** 决策标题。 */
    private final String title;

    /** 决策正文。 */
    private final String decisionText;

    /** 决策依据。 */
    private final String rationale;

    /** 支撑该决策的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;

    /** 决策创建时间。 */
    private final Instant createdAt;

    /** 决策更新时间。 */
    private final Instant updatedAt;
}
