package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * 当前未闭环事项。
 */
@Getter
@Builder
public class OpenLoop {

    /** 未闭环事项 ID。 */
    private final String openLoopId;

    /** 未闭环事项类型。 */
    private final OpenLoopKind kind;

    /** 未闭环事项状态。 */
    private final OpenLoopStatus status;

    /** 未闭环事项标题。 */
    private final String title;

    /** 未闭环事项描述。 */
    private final String description;

    /** 支撑该未闭环事项的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;

    /** 未闭环事项创建时间。 */
    private final Instant createdAt;

    /** 未闭环事项更新时间。 */
    private final Instant updatedAt;
}
