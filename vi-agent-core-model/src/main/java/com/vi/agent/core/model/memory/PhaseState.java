package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * 当前任务阶段与功能开关状态。
 */
@Getter
@Builder
@Jacksonized
public class PhaseState {

    /** 是否启用 prompt 管理。 */
    private final Boolean promptEngineeringEnabled;

    /** 是否启用 context 审计。 */
    private final Boolean contextAuditEnabled;

    /** 是否启用 summary。 */
    private final Boolean summaryEnabled;

    /** 是否启用 state extraction。 */
    private final Boolean stateExtractionEnabled;

    /** 是否启用 compaction。 */
    private final Boolean compactionEnabled;
}
