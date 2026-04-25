package com.vi.agent.core.runtime.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * post-turn session memory 更新结果。
 */
@Getter
@Builder(toBuilder = true)
public class SessionMemoryUpdateResult {

    /** 是否成功。 */
    private final boolean success;

    /** 是否发生降级。 */
    private final boolean degraded;

    /** 是否跳过 memory update。 */
    private final boolean skipped;

    /** state 内部任务 ID。 */
    private final String stateTaskId;

    /** summary 内部任务 ID。 */
    private final String summaryTaskId;

    /** 新 state 版本。 */
    private final Long newStateVersion;

    /** 新 summary 版本。 */
    private final Long newSummaryVersion;

    /** 保存的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;

    /** 保存的 evidence 数量。 */
    private final int evidenceSavedCount;

    /** 失败或降级原因。 */
    private final String failureReason;
}
