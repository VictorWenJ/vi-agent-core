package com.vi.agent.core.runtime.memory.evidence;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Evidence 绑定结果。
 */
@Getter
@Builder(toBuilder = true)
public class EvidenceBindingResult {

    /** 是否成功完成绑定流程。 */
    private final boolean success;

    /** 是否发生降级。 */
    private final boolean degraded;

    /** 是否跳过 evidence 绑定。 */
    private final boolean skipped;

    /** 已保存的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;

    /** 已保存 evidence 数量。 */
    private final int savedCount;

    /** 失败或降级原因。 */
    private final String failureReason;
}
