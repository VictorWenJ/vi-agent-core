package com.vi.agent.core.model.memory.statepatch;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

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

    /** prompt 管理开关更新值，null 表示不修改。 */
    Boolean promptEngineeringEnabled;

    /** context 审计开关更新值，null 表示不修改。 */
    Boolean contextAuditEnabled;

    /** summary 开关更新值，null 表示不修改。 */
    Boolean summaryEnabled;

    /** state extraction 开关更新值，null 表示不修改。 */
    Boolean stateExtractionEnabled;

    /** compaction 开关更新值，null 表示不修改。 */
    Boolean compactionEnabled;

    /**
     * 判断该阶段补丁是否没有任何显式更新字段。
     *
     * @return 没有显式更新字段时返回 true
     */
    public boolean isEmpty() {
        return Objects.isNull(promptEngineeringEnabled)
            && Objects.isNull(contextAuditEnabled)
            && Objects.isNull(summaryEnabled)
            && Objects.isNull(stateExtractionEnabled)
            && Objects.isNull(compactionEnabled);
    }
}
