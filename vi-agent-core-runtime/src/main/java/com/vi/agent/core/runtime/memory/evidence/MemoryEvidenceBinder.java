package com.vi.agent.core.runtime.memory.evidence;

import com.vi.agent.core.common.id.EvidenceIdGenerator;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.EvidenceRef;
import com.vi.agent.core.model.memory.EvidenceSource;
import com.vi.agent.core.model.memory.EvidenceTarget;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.statepatch.PhaseStatePatch;
import com.vi.agent.core.model.memory.statepatch.UserPreferencePatch;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MemoryEvidenceRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Memory evidence 绑定器。
 */
@Slf4j
@Service
public class MemoryEvidenceBinder {

    private static final double DEFAULT_CONFIDENCE = 1.0D;

    /** memory evidence 仓储。 */
    @Resource
    private MemoryEvidenceRepository memoryEvidenceRepository;

    /** evidence ID 生成器。 */
    @Resource
    private EvidenceIdGenerator evidenceIdGenerator;

    /** evidence target 工厂。 */
    @Resource
    private EvidenceTargetFactory targetFactory;

    /** evidence source 工厂。 */
    @Resource
    private EvidenceSourceFactory sourceFactory;

    public EvidenceBindingResult bind(EvidenceBindingCommand command) {
        try {
            List<String> warnings = new ArrayList<>();
            List<EvidenceRef> evidenceRefs = buildEvidenceRefs(command, warnings);
            if (evidenceRefs.isEmpty()) {
                return EvidenceBindingResult.builder()
                    .success(true)
                    .skipped(true)
                    .failureReason(String.join("; ", warnings))
                    .build();
            }
            memoryEvidenceRepository.saveAll(evidenceRefs);
            return EvidenceBindingResult.builder()
                .success(warnings.isEmpty())
                .degraded(!warnings.isEmpty())
                .evidenceIds(evidenceRefs.stream().map(EvidenceRef::getEvidenceId).toList())
                .stateEvidenceIds(stateEvidenceIds(evidenceRefs))
                .summaryEvidenceIds(summaryEvidenceIds(evidenceRefs))
                .savedCount(evidenceRefs.size())
                .failureReason(String.join("; ", warnings))
                .build();
        } catch (Exception ex) {
            log.warn("Memory evidence binding failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return EvidenceBindingResult.builder()
                .success(false)
                .degraded(true)
                .savedCount(0)
                .failureReason("evidence save failed: " + ex.getMessage())
                .build();
        }
    }

    private List<EvidenceRef> buildEvidenceRefs(EvidenceBindingCommand command, List<String> warnings) {
        if (command == null) {
            return List.of();
        }
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        StateDelta delta = command.getStateDelta();
        SessionStateSnapshot newState = command.getNewState();
        if (delta != null && !delta.isEmpty() && newState != null) {
            evidenceRefs.addAll(stateEvidence(command, newState, delta, warnings));
        }
        ConversationSummary newSummary = command.getNewSummary();
        if (newSummary != null && StringUtils.isNotBlank(newSummary.getSummaryText())) {
            sourceForSummary(command, warnings)
                .ifPresent(source -> evidenceRefs.add(newEvidence(targetFactory.summaryText(newSummary), source)));
        }
        return evidenceRefs;
    }

    private List<EvidenceRef> stateEvidence(
        EvidenceBindingCommand command,
        SessionStateSnapshot newState,
        StateDelta delta,
        List<String> warnings
    ) {
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        sourceForState(command, warnings).ifPresent(source -> {
            if (StringUtils.isNotBlank(delta.getTaskGoalOverride())) {
                evidenceRefs.add(newEvidence(targetFactory.sessionStateField(
                    newState.getSnapshotId(),
                    "taskGoal",
                    "taskGoal",
                    "sessionState.taskGoal"
                ), source));
            }
            if (delta.getWorkingModeOverride() != null) {
                evidenceRefs.add(newEvidence(targetFactory.sessionStateField(
                    newState.getSnapshotId(),
                    "workingMode",
                    "workingMode",
                    "sessionState.workingMode"
                ), source));
            }
            for (ConfirmedFactRecord fact : nullSafe(delta.getConfirmedFactsAppend())) {
                evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "confirmedFacts", fact.getFactId(), "confirmedFacts[" + fact.getFactId() + "]"), source));
            }
            for (ConstraintRecord constraint : nullSafe(delta.getConstraintsAppend())) {
                evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "constraints", constraint.getConstraintId(), "constraints[" + constraint.getConstraintId() + "]"), source));
            }
            for (DecisionRecord decision : nullSafe(delta.getDecisionsAppend())) {
                evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "decisions", decision.getDecisionId(), "decisions[" + decision.getDecisionId() + "]"), source));
            }
            for (OpenLoop openLoop : nullSafe(delta.getOpenLoopsAppend())) {
                evidenceRefs.add(newEvidence(targetFactory.openLoop(newState.getSnapshotId(), openLoop.getLoopId(), "openLoops[" + openLoop.getLoopId() + "]"), source));
            }
            for (String loopId : nullSafe(delta.getOpenLoopIdsToClose())) {
                evidenceRefs.add(newEvidence(targetFactory.openLoop(newState.getSnapshotId(), loopId, "openLoops[" + loopId + "].closedAt"), source));
            }
            addUserPreferenceEvidence(evidenceRefs, newState, delta.getUserPreferencesPatch(), source);
            addPhaseStateEvidence(evidenceRefs, newState, delta.getPhaseStatePatch(), source);
        });
        for (ToolOutcomeDigest digest : nullSafe(delta.getRecentToolOutcomesAppend())) {
            sourceFactory.fromToolOutcome(
                digest,
                command.getSessionId(),
                command.getTurnId(),
                command.getRunId(),
                command.getWorkingContextSnapshotId(),
                command.getStateTaskId()
            ).ifPresent(source -> evidenceRefs.add(newEvidence(targetFactory.toolOutcome(newState.getSnapshotId(), digest), source)));
        }
        return evidenceRefs;
    }

    private void addUserPreferenceEvidence(
        List<EvidenceRef> evidenceRefs,
        SessionStateSnapshot newState,
        UserPreferencePatch patch,
        EvidenceSource source
    ) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        if (patch.getAnswerStyle() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "userPreferences.answerStyle", "answerStyle", "userPreferences.answerStyle"), source));
        }
        if (patch.getDetailLevel() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "userPreferences.detailLevel", "detailLevel", "userPreferences.detailLevel"), source));
        }
        if (patch.getTermFormat() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "userPreferences.termFormat", "termFormat", "userPreferences.termFormat"), source));
        }
    }

    private void addPhaseStateEvidence(
        List<EvidenceRef> evidenceRefs,
        SessionStateSnapshot newState,
        PhaseStatePatch patch,
        EvidenceSource source
    ) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        if (patch.getPromptEngineeringEnabled() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "phaseState.promptEngineeringEnabled", "promptEngineeringEnabled", "phaseState.promptEngineeringEnabled"), source));
        }
        if (patch.getContextAuditEnabled() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "phaseState.contextAuditEnabled", "contextAuditEnabled", "phaseState.contextAuditEnabled"), source));
        }
        if (patch.getSummaryEnabled() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "phaseState.summaryEnabled", "summaryEnabled", "phaseState.summaryEnabled"), source));
        }
        if (patch.getStateExtractionEnabled() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "phaseState.stateExtractionEnabled", "stateExtractionEnabled", "phaseState.stateExtractionEnabled"), source));
        }
        if (patch.getCompactionEnabled() != null) {
            evidenceRefs.add(newEvidence(targetFactory.sessionStateField(newState.getSnapshotId(), "phaseState.compactionEnabled", "compactionEnabled", "phaseState.compactionEnabled"), source));
        }
    }

    private Optional<EvidenceSource> sourceForState(EvidenceBindingCommand command, List<String> warnings) {
        Optional<EvidenceSource> candidateSource = sourceFromCandidates(command, command.getStateTaskId());
        if (candidateSource.isPresent()) {
            return candidateSource;
        }
        Optional<EvidenceSource> fallbackSource = firstTurnMessageSource(command, command.getStateTaskId());
        if (fallbackSource.isPresent()) {
            if (CollectionUtils.isNotEmpty(command.getSourceCandidateIds())) {
                warnings.add("sourceCandidateIds invalid; fallback to completed turn message");
            }
            return fallbackSource;
        }
        if (StringUtils.isNotBlank(command.getStateTaskId())) {
            return Optional.of(sourceFactory.fromInternalTask(command.getSessionId(), command.getTurnId(), command.getRunId(), command.getStateTaskId(), "STATE_EXTRACT"));
        }
        warnings.add("state evidence skipped because no trusted source exists");
        return Optional.empty();
    }

    private Optional<EvidenceSource> sourceForSummary(EvidenceBindingCommand command, List<String> warnings) {
        Optional<EvidenceSource> messageSource = firstTurnMessageSource(command, command.getSummaryTaskId());
        if (messageSource.isPresent()) {
            return messageSource;
        }
        if (StringUtils.isNotBlank(command.getSummaryTaskId())) {
            return Optional.of(sourceFactory.fromInternalTask(command.getSessionId(), command.getTurnId(), command.getRunId(), command.getSummaryTaskId(), "SUMMARY_EXTRACT"));
        }
        warnings.add("summary evidence skipped because no trusted source exists");
        return Optional.empty();
    }

    private Optional<EvidenceSource> sourceFromCandidates(EvidenceBindingCommand command, String internalTaskId) {
        if (CollectionUtils.isEmpty(command.getSourceCandidateIds()) || CollectionUtils.isEmpty(command.getTurnMessages())) {
            return Optional.empty();
        }
        Map<String, Message> messagesById = new LinkedHashMap<>();
        for (Message message : command.getTurnMessages()) {
            if (message != null && StringUtils.isNotBlank(message.getMessageId())) {
                messagesById.put(message.getMessageId(), message);
            }
        }
        for (String candidateId : command.getSourceCandidateIds()) {
            Message message = messagesById.get(candidateId);
            if (message == null) {
                continue;
            }
            Optional<EvidenceSource> source = sourceFactory.fromMessage(message, command.getWorkingContextSnapshotId(), internalTaskId);
            if (source.isPresent()) {
                return source;
            }
        }
        return Optional.empty();
    }

    private Optional<EvidenceSource> firstTurnMessageSource(EvidenceBindingCommand command, String internalTaskId) {
        for (Message message : nullSafe(command.getTurnMessages())) {
            Optional<EvidenceSource> source = sourceFactory.fromMessage(message, command.getWorkingContextSnapshotId(), internalTaskId);
            if (source.isPresent()) {
                return source;
            }
        }
        return Optional.empty();
    }

    private EvidenceRef newEvidence(EvidenceTarget target, EvidenceSource source) {
        return EvidenceRef.builder()
            .evidenceId(evidenceIdGenerator.nextId())
            .target(target)
            .source(source)
            .confidence(DEFAULT_CONFIDENCE)
            .createdAt(Instant.now())
            .build();
    }

    private List<String> stateEvidenceIds(List<EvidenceRef> evidenceRefs) {
        return evidenceRefs.stream()
            .filter(evidence -> evidence != null && evidence.getTarget() != null)
            .filter(evidence -> evidence.getTarget().getTargetType() != EvidenceTargetType.SUMMARY_SEGMENT)
            .map(EvidenceRef::getEvidenceId)
            .toList();
    }

    private List<String> summaryEvidenceIds(List<EvidenceRef> evidenceRefs) {
        return evidenceRefs.stream()
            .filter(evidence -> evidence != null && evidence.getTarget() != null)
            .filter(evidence -> evidence.getTarget().getTargetType() == EvidenceTargetType.SUMMARY_SEGMENT)
            .map(EvidenceRef::getEvidenceId)
            .toList();
    }

    private <T> List<T> nullSafe(List<T> items) {
        return items == null ? List.of() : items;
    }
}
