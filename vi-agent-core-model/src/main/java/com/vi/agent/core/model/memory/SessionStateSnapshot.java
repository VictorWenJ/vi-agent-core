package com.vi.agent.core.model.memory;

import com.vi.agent.core.model.context.WorkingMode;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * 结构化 session state 快照。
 */
@Getter
@Builder
@Jacksonized
public class SessionStateSnapshot {

    /** session state 快照 ID。 */
    private final String snapshotId;

    /** 当前 session ID。 */
    private final String sessionId;

    /** session state 版本。 */
    private final Long stateVersion;

    /** 当前任务目标。 */
    private final String taskGoal;

    /** 当前工作模式。 */
    private final WorkingMode workingMode;

    /** 已确认事实列表。 */
    @Singular("confirmedFact")
    private final List<ConfirmedFactRecord> confirmedFacts;

    /** 当前有效约束列表。 */
    @Singular("constraint")
    private final List<ConstraintRecord> constraints;

    /** 已记录决策列表。 */
    @Singular("decision")
    private final List<DecisionRecord> decisions;

    /** 用户偏好状态。 */
    private final UserPreferenceState userPreference;

    /** 当前未闭环事项列表。 */
    @Singular("openLoop")
    private final List<OpenLoop> openLoops;

    /** 重要工具结果摘要列表。 */
    @Singular("toolOutcomeDigest")
    private final List<ToolOutcomeDigest> toolOutcomeDigests;

    /** 当前任务阶段状态。 */
    private final PhaseState phaseState;

    /** 产生该快照的 run ID。 */
    private final String sourceRunId;

    /** 快照创建时间。 */
    private final Instant createdAt;

    /** 快照更新时间。 */
    private final Instant updatedAt;
}