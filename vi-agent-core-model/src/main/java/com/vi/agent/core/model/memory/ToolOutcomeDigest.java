package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 重要工具结果摘要。
 */
@Getter
@Builder
@Jacksonized
public class ToolOutcomeDigest {

    /** 摘要 ID。 */
    private final String digestId;

    /** tool call record ID。 */
    private final String toolCallRecordId;

    /** tool execution ID。 */
    private final String toolExecutionId;

    /** 工具名称。 */
    private final String toolName;

    /** 摘要内容。 */
    private final String summary;

    /** 实时性策略。 */
    private final ToolOutcomeFreshnessPolicy freshnessPolicy;

    /** 按 TTL 计算出的有效期。 */
    private final Instant validUntil;

    /** 最后验证时间。 */
    private final Instant lastVerifiedAt;
}
