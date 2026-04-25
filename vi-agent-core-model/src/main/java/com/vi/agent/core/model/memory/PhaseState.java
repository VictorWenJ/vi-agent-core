package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 当前任务阶段状态。
 */
@Getter
@Builder
public class PhaseState {

    /** 阶段 key。 */
    private final String phaseKey;

    /** 阶段名称。 */
    private final String phaseName;

    /** 阶段状态。 */
    private final String status;

    /** 阶段状态更新时间。 */
    private final Instant updatedAt;
}
