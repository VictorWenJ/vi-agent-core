package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * 重要工具结果摘要。
 */
@Getter
@Builder
@Jacksonized
public class ToolOutcomeDigest {

    /** 工具结果摘要 ID。 */
    private final String digestId;

    /** 工具调用记录 ID。 */
    private final String toolCallRecordId;

    /** 工具执行记录 ID。 */
    private final String toolExecutionId;

    /** 工具名称。 */
    private final String toolName;

    /** 工具结果摘要文本。 */
    private final String digestText;

    /** 工具结果新鲜度策略。 */
    private final ToolOutcomeFreshnessPolicy freshnessPolicy;

    /** 工具结果过期时间。 */
    private final Instant expiresAt;

    /** 支撑该工具结果摘要的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;
}
