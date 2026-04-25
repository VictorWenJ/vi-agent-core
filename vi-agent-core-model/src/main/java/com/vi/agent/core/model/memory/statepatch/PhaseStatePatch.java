package com.vi.agent.core.model.memory.statepatch;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Objects;

/**
 * 任务阶段状态的字段级补丁。
 *
 * <p>该对象只表达本轮显式更新的阶段字段，字段为 null 表示不修改对应旧值。</p>
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PhaseStatePatch {

    /** 阶段 key 更新值，使用既有 PhaseState 字段口径，null 表示不修改。 */
    private final String phaseKey;

    /** 阶段名称更新值，使用既有 PhaseState 字段口径，null 表示不修改。 */
    private final String phaseName;

    /** 阶段状态更新值，使用既有 PhaseState 字段口径，null 表示不修改。 */
    private final String status;

    /** 阶段状态更新时间更新值，null 表示不修改。 */
    private final Instant updatedAt;

    /**
     * 判断该阶段补丁是否没有任何显式更新字段。
     *
     * @return 没有显式更新字段时返回 true
     */
    public boolean isEmpty() {
        return Objects.isNull(phaseKey)
            && Objects.isNull(phaseName)
            && Objects.isNull(status)
            && Objects.isNull(updatedAt);
    }
}
