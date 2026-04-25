package com.vi.agent.core.model.memory;

import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.statepatch.PhaseStatePatch;
import com.vi.agent.core.model.memory.statepatch.UserPreferencePatch;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Objects;

/**
 * 本轮相对上一版 session state 的领域补丁包。
 *
 * <p>该对象只表达本轮识别出的状态变更意图，不再承载通用 CRUD 或 upsert/remove 语义。</p>
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class StateDelta {

    /** 当前 session ID，用于标识补丁归属的会话。 */
    String sessionId;

    /** 产生该补丁的 run ID，用于审计和问题追踪。 */
    String sourceRunId;

    /** 应用补丁前的 session state 版本。 */
    Long previousStateVersion;

    /** 应用补丁后的目标 session state 版本。 */
    Long targetStateVersion;

    /** 任务目标覆盖值，存在时覆盖旧任务目标。 */
    String taskGoalOverride;

    /** 需要追加进入已确认事实列表的事实记录。 */
    @Singular("confirmedFactAppend")
    List<ConfirmedFactRecord> confirmedFactsAppend;

    /** 需要追加进入约束列表的约束记录。 */
    @Singular("constraintAppend")
    List<ConstraintRecord> constraintsAppend;

    /** 用户偏好的字段级补丁，仅非空字段参与浅合并。 */
    UserPreferencePatch userPreferencesPatch;

    /** 需要追加进入决策列表的决策记录。 */
    @Singular("decisionAppend")
    List<DecisionRecord> decisionsAppend;

    /** 需要追加进入未闭环事项列表的新事项。 */
    @Singular("openLoopAppend")
    List<OpenLoop> openLoopsAppend;

    /** 需要按 loopId 关闭的未闭环事项 ID。 */
    @Singular("openLoopIdToClose")
    List<String> openLoopIdsToClose;

    /** 需要追加进入最近工具结果摘要列表的记录。 */
    @Singular("recentToolOutcomeAppend")
    List<ToolOutcomeDigest> recentToolOutcomesAppend;

    /** 工作模式覆盖值，存在时覆盖旧工作模式。 */
    WorkingMode workingModeOverride;

    /** 任务阶段状态的字段级补丁，仅非空字段参与合并。 */
    PhaseStatePatch phaseStatePatch;

    /** 参与生成该补丁的候选项 ID，仅用于 evidence / audit，不直接写入最终 state 主体。 */
    @Singular("sourceCandidateId")
    List<String> sourceCandidateIds;

    /**
     * 判断该补丁是否没有任何状态变更。
     *
     * <p>元数据字段和候选项来源只用于审计，不计入状态变更判断。</p>
     *
     * @return 没有状态变更时返回 true
     */
    public boolean isEmpty() {
        return Objects.isNull(taskGoalOverride)
            && isEmpty(confirmedFactsAppend)
            && isEmpty(constraintsAppend)
            && isEmpty(userPreferencesPatch)
            && isEmpty(decisionsAppend)
            && isEmpty(openLoopsAppend)
            && isEmpty(openLoopIdsToClose)
            && isEmpty(recentToolOutcomesAppend)
            && Objects.isNull(workingModeOverride)
            && isEmpty(phaseStatePatch);
    }

    private boolean isEmpty(List<?> items) {
        return Objects.isNull(items) || items.isEmpty();
    }

    private boolean isEmpty(UserPreferencePatch patch) {
        return Objects.isNull(patch) || patch.isEmpty();
    }

    private boolean isEmpty(PhaseStatePatch patch) {
        return Objects.isNull(patch) || patch.isEmpty();
    }
}
