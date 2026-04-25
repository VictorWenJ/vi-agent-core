package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 已确认事实记录。
 */
@Getter
@Builder
public class ConfirmedFactRecord {

    /** 已确认事实 ID。 */
    private final String factId;

    /** 事实分类。 */
    private final String category;

    /** 事实内容。 */
    private final String content;

    /** 事实置信度。 */
    private final BigDecimal confidence;

    /** 支撑该事实的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;

    /** 事实创建时间。 */
    private final Instant createdAt;

    /** 事实更新时间。 */
    private final Instant updatedAt;
}
