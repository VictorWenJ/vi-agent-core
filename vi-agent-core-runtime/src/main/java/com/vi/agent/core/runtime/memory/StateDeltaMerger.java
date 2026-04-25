package com.vi.agent.core.runtime.memory;

import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.OpenLoopStatus;
import com.vi.agent.core.model.memory.PhaseState;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.UserPreferenceState;
import com.vi.agent.core.model.memory.statepatch.PhaseStatePatch;
import com.vi.agent.core.model.memory.statepatch.UserPreferencePatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 将 StateDelta 领域补丁合并到当前 SessionStateSnapshot。
 *
 * <p>该合并器只实现 P2 冻结设计里的显式领域规则，不提供通用反射式 patch 能力。</p>
 */
public class StateDeltaMerger {

    /** 最近工具结果摘要最多保留条数。 */
    public static final int MAX_RECENT_TOOL_OUTCOMES = 20;

    /**
     * 合并当前状态与本轮状态补丁。
     *
     * @param current 当前 session state 快照
     * @param delta 本轮领域补丁
     * @return 合并后的 session state 快照
     */
    public SessionStateSnapshot merge(SessionStateSnapshot current, StateDelta delta) {
        Objects.requireNonNull(current, "current session state must not be null");
        if (Objects.isNull(delta) || delta.isEmpty()) {
            return current;
        }

        return SessionStateSnapshot.builder()
            .snapshotId(current.getSnapshotId())
            .sessionId(current.getSessionId())
            .stateVersion(resolveStateVersion(current, delta))
            .taskGoal(resolveTaskGoal(current, delta))
            .workingMode(resolveWorkingMode(current, delta))
            .confirmedFacts(appendDistinctById(
                current.getConfirmedFacts(),
                delta.getConfirmedFactsAppend(),
                ConfirmedFactRecord::getFactId
            ))
            .constraints(appendDistinctById(
                current.getConstraints(),
                delta.getConstraintsAppend(),
                ConstraintRecord::getConstraintId
            ))
            .decisions(appendDistinctById(
                current.getDecisions(),
                delta.getDecisionsAppend(),
                DecisionRecord::getDecisionId
            ))
            .userPreference(mergeUserPreference(current.getUserPreference(), delta.getUserPreferencesPatch()))
            .openLoops(mergeOpenLoops(current.getOpenLoops(), delta))
            .toolOutcomeDigests(mergeRecentToolOutcomes(
                current.getToolOutcomeDigests(),
                delta.getRecentToolOutcomesAppend()
            ))
            .phaseState(mergePhaseState(current.getPhaseState(), delta.getPhaseStatePatch()))
            .sourceRunId(resolveSourceRunId(current, delta))
            .createdAt(current.getCreatedAt())
            .updatedAt(current.getUpdatedAt())
            .build();
    }

    private Long resolveStateVersion(SessionStateSnapshot current, StateDelta delta) {
        if (Objects.nonNull(delta.getTargetStateVersion())) {
            return delta.getTargetStateVersion();
        }
        return current.getStateVersion();
    }

    private String resolveTaskGoal(SessionStateSnapshot current, StateDelta delta) {
        if (Objects.nonNull(delta.getTaskGoalOverride())) {
            return delta.getTaskGoalOverride();
        }
        return current.getTaskGoal();
    }

    private com.vi.agent.core.model.context.WorkingMode resolveWorkingMode(
        SessionStateSnapshot current,
        StateDelta delta
    ) {
        if (Objects.nonNull(delta.getWorkingModeOverride())) {
            return delta.getWorkingModeOverride();
        }
        return current.getWorkingMode();
    }

    private String resolveSourceRunId(SessionStateSnapshot current, StateDelta delta) {
        if (Objects.nonNull(delta.getSourceRunId())) {
            return delta.getSourceRunId();
        }
        return current.getSourceRunId();
    }

    private <T> List<T> appendDistinctById(List<T> current, List<T> appended, Function<T, String> idExtractor) {
        List<T> result = new ArrayList<>(nullSafe(current));
        Set<String> seenIds = new LinkedHashSet<>();
        for (T item : result) {
            String id = idExtractor.apply(item);
            if (Objects.nonNull(id)) {
                seenIds.add(id);
            }
        }
        for (T item : nullSafe(appended)) {
            String id = idExtractor.apply(item);
            if (Objects.isNull(id) || seenIds.add(id)) {
                result.add(item);
            }
        }
        return result;
    }

    private UserPreferenceState mergeUserPreference(UserPreferenceState current, UserPreferencePatch patch) {
        if (Objects.isNull(patch) || patch.isEmpty()) {
            return current;
        }
        return UserPreferenceState.builder()
            .answerStyle(Objects.nonNull(patch.getAnswerStyle())
                ? patch.getAnswerStyle()
                : Objects.nonNull(current) ? current.getAnswerStyle() : null)
            .detailLevel(Objects.nonNull(patch.getDetailLevel())
                ? patch.getDetailLevel()
                : Objects.nonNull(current) ? current.getDetailLevel() : null)
            .termFormat(Objects.nonNull(patch.getTermFormat())
                ? patch.getTermFormat()
                : Objects.nonNull(current) ? current.getTermFormat() : null)
            .build();
    }

    private List<OpenLoop> mergeOpenLoops(List<OpenLoop> current, StateDelta delta) {
        Set<String> closeIds = new HashSet<>(nullSafe(delta.getOpenLoopIdsToClose()));
        List<OpenLoop> merged = new ArrayList<>();
        for (OpenLoop openLoop : nullSafe(current)) {
            if (closeIds.contains(openLoop.getOpenLoopId())) {
                merged.add(closeOpenLoop(openLoop));
            } else {
                merged.add(openLoop);
            }
        }
        merged.addAll(nullSafe(delta.getOpenLoopsAppend()));
        return merged;
    }

    private OpenLoop closeOpenLoop(OpenLoop openLoop) {
        return OpenLoop.builder()
            .openLoopId(openLoop.getOpenLoopId())
            .kind(openLoop.getKind())
            .status(OpenLoopStatus.CLOSED)
            .title(openLoop.getTitle())
            .description(openLoop.getDescription())
            .evidenceIds(nullSafe(openLoop.getEvidenceIds()))
            .createdAt(openLoop.getCreatedAt())
            .updatedAt(openLoop.getUpdatedAt())
            .build();
    }

    private List<ToolOutcomeDigest> mergeRecentToolOutcomes(
        List<ToolOutcomeDigest> current,
        List<ToolOutcomeDigest> appended
    ) {
        List<ToolOutcomeDigest> merged = new ArrayList<>(nullSafe(current));
        merged.addAll(nullSafe(appended));
        if (merged.size() <= MAX_RECENT_TOOL_OUTCOMES) {
            return merged;
        }
        return new ArrayList<>(merged.subList(merged.size() - MAX_RECENT_TOOL_OUTCOMES, merged.size()));
    }

    private PhaseState mergePhaseState(PhaseState current, PhaseStatePatch patch) {
        if (Objects.isNull(patch) || patch.isEmpty()) {
            return current;
        }
        return PhaseState.builder()
            .promptEngineeringEnabled(Objects.nonNull(patch.getPromptEngineeringEnabled())
                ? patch.getPromptEngineeringEnabled()
                : Objects.nonNull(current) ? current.getPromptEngineeringEnabled() : null)
            .contextAuditEnabled(Objects.nonNull(patch.getContextAuditEnabled())
                ? patch.getContextAuditEnabled()
                : Objects.nonNull(current) ? current.getContextAuditEnabled() : null)
            .summaryEnabled(Objects.nonNull(patch.getSummaryEnabled())
                ? patch.getSummaryEnabled()
                : Objects.nonNull(current) ? current.getSummaryEnabled() : null)
            .stateExtractionEnabled(Objects.nonNull(patch.getStateExtractionEnabled())
                ? patch.getStateExtractionEnabled()
                : Objects.nonNull(current) ? current.getStateExtractionEnabled() : null)
            .compactionEnabled(Objects.nonNull(patch.getCompactionEnabled())
                ? patch.getCompactionEnabled()
                : Objects.nonNull(current) ? current.getCompactionEnabled() : null)
            .build();
    }

    private <T> List<T> nullSafe(List<T> items) {
        if (Objects.isNull(items)) {
            return List.of();
        }
        return items;
    }
}
