package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * 本轮相对上一版 session state 的增量补丁。
 */
@Getter
@Builder
public class StateDelta {

    /** 当前 session ID。 */
    private final String sessionId;

    /** 产生该增量的 run ID。 */
    private final String sourceRunId;

    /** 上一版 session state 版本。 */
    private final Long previousStateVersion;

    /** 目标 session state 版本。 */
    private final Long targetStateVersion;

    /** 需要新增或更新的已确认事实。 */
    @Singular("upsertConfirmedFact")
    private final List<ConfirmedFactRecord> upsertConfirmedFacts;

    /** 需要移除的已确认事实 ID。 */
    @Singular("removeConfirmedFactId")
    private final List<String> removeConfirmedFactIds;

    /** 需要新增或更新的约束。 */
    @Singular("upsertConstraint")
    private final List<ConstraintRecord> upsertConstraints;

    /** 需要移除的约束 ID。 */
    @Singular("removeConstraintId")
    private final List<String> removeConstraintIds;

    /** 需要新增或更新的决策。 */
    @Singular("upsertDecision")
    private final List<DecisionRecord> upsertDecisions;

    /** 需要移除的决策 ID。 */
    @Singular("removeDecisionId")
    private final List<String> removeDecisionIds;

    /** 需要更新的用户偏好状态。 */
    private final UserPreferenceState userPreference;

    /** 需要新增或更新的未闭环事项。 */
    @Singular("upsertOpenLoop")
    private final List<OpenLoop> upsertOpenLoops;

    /** 需要关闭的未闭环事项 ID。 */
    @Singular("closeOpenLoopId")
    private final List<String> closeOpenLoopIds;

    /** 需要新增或更新的工具结果摘要。 */
    @Singular("upsertToolOutcomeDigest")
    private final List<ToolOutcomeDigest> upsertToolOutcomeDigests;

    /** 需要更新的任务阶段状态。 */
    private final PhaseState phaseState;
}
