package com.vi.agent.core.runtime.memory;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.port.MemoryEvidenceRepository;
import com.vi.agent.core.model.port.SessionStateCacheRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.SessionSummaryCacheRepository;
import com.vi.agent.core.model.port.SessionSummaryRepository;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskCommand;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskResult;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates deterministic post-turn session memory update.
 */
@Slf4j
@Service
public class SessionMemoryCoordinator {

    @Resource
    private SessionMemoryProperties properties;

    @Resource
    private SessionStateRepository sessionStateRepository;

    @Resource
    private SessionSummaryRepository sessionSummaryRepository;

    @Resource
    private SessionStateCacheRepository sessionStateCacheRepository;

    @Resource
    private SessionSummaryCacheRepository sessionSummaryCacheRepository;

    @Resource
    private MemoryEvidenceRepository memoryEvidenceRepository;

    @Resource
    private InternalMemoryTaskService internalMemoryTaskService;

    @Resource
    private StateDeltaMerger stateDeltaMerger;

    public SessionMemoryUpdateResult updateAfterTurn(SessionMemoryUpdateCommand command) {
        if (properties != null && !properties.isPostTurnUpdateEnabled()) {
            return SessionMemoryUpdateResult.builder()
                .success(true)
                .skipped(true)
                .build();
        }
        if (properties != null
            && !properties.isStateExtractionEnabled()
            && !properties.isSummaryUpdateEnabled()) {
            return SessionMemoryUpdateResult.builder()
                .success(true)
                .skipped(true)
                .build();
        }

        List<String> failureReasons = new ArrayList<>();
        MemoryUpdatePartial stateUpdate = MemoryUpdatePartial.empty();
        MemoryUpdatePartial summaryUpdate = MemoryUpdatePartial.empty();

        try {
            if (properties == null || properties.isStateExtractionEnabled()) {
                stateUpdate = updateState(command);
                collectFailure(failureReasons, stateUpdate.failureReason());
            }
            if (properties == null || properties.isSummaryUpdateEnabled()) {
                summaryUpdate = updateSummary(command);
                collectFailure(failureReasons, summaryUpdate.failureReason());
            }
        } catch (Exception ex) {
            log.warn("Post-turn session memory update failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            failureReasons.add(ex.getMessage());
        }

        boolean degraded = !failureReasons.isEmpty() || stateUpdate.degraded() || summaryUpdate.degraded();
        return SessionMemoryUpdateResult.builder()
            .success(!degraded)
            .degraded(degraded)
            .skipped(false)
            .stateTaskId(stateUpdate.taskId())
            .summaryTaskId(summaryUpdate.taskId())
            .newStateVersion(stateUpdate.newVersion())
            .newSummaryVersion(summaryUpdate.newVersion())
            .failureReason(String.join("; ", failureReasons))
            .build();
    }

    private MemoryUpdatePartial updateState(SessionMemoryUpdateCommand command) {
        try {
            Optional<SessionStateSnapshot> latestState = sessionStateRepository.findLatestBySessionId(command.getSessionId());
            InternalMemoryTaskResult taskResult = internalMemoryTaskService.execute(toTaskCommand(command, InternalTaskType.STATE_EXTRACTION));
            if (!taskResult.isSuccess() || taskResult.isDegraded()) {
                return MemoryUpdatePartial.degraded(taskResult.getInternalTaskId(), taskResult.getFailureReason());
            }

            StateDelta stateDelta = taskResult.getStateDelta();
            if (stateDelta == null || stateDelta.isEmpty()) {
                return MemoryUpdatePartial.success(taskResult.getInternalTaskId(), null);
            }

            SessionStateSnapshot baseState = latestState.orElseGet(() -> emptyState(command));
            SessionStateSnapshot merged = stateDeltaMerger.merge(baseState, stateDelta);
            SessionStateSnapshot nextState = copyAsNextState(baseState, merged);
            sessionStateRepository.save(nextState);
            String redisFailure = refreshStateCache(nextState);
            if (redisFailure != null) {
                return MemoryUpdatePartial.degraded(taskResult.getInternalTaskId(), nextState.getStateVersion(), redisFailure);
            }
            return MemoryUpdatePartial.success(taskResult.getInternalTaskId(), nextState.getStateVersion());
        } catch (Exception ex) {
            log.warn("Post-turn state memory update failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return MemoryUpdatePartial.degraded(null, ex.getMessage());
        }
    }

    private MemoryUpdatePartial updateSummary(SessionMemoryUpdateCommand command) {
        try {
            sessionSummaryRepository.findLatestBySessionId(command.getSessionId());
            InternalMemoryTaskResult taskResult = internalMemoryTaskService.execute(toTaskCommand(command, InternalTaskType.SUMMARY_UPDATE));
            if (!taskResult.isSuccess() || taskResult.isDegraded()) {
                return MemoryUpdatePartial.degraded(taskResult.getInternalTaskId(), taskResult.getFailureReason());
            }
            ConversationSummary summary = taskResult.getSummary();
            if (summary == null || taskResult.isSkipped()) {
                return MemoryUpdatePartial.success(taskResult.getInternalTaskId(), null);
            }
            sessionSummaryRepository.save(summary);
            String redisFailure = refreshSummaryCache(summary);
            if (redisFailure != null) {
                return MemoryUpdatePartial.degraded(taskResult.getInternalTaskId(), summary.getSummaryVersion(), redisFailure);
            }
            return MemoryUpdatePartial.success(taskResult.getInternalTaskId(), summary.getSummaryVersion());
        } catch (Exception ex) {
            log.warn("Post-turn summary memory update failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return MemoryUpdatePartial.degraded(null, ex.getMessage());
        }
    }

    private InternalMemoryTaskCommand toTaskCommand(SessionMemoryUpdateCommand command, InternalTaskType taskType) {
        return InternalMemoryTaskCommand.builder()
            .taskType(taskType)
            .conversationId(command == null ? null : command.getConversationId())
            .sessionId(command == null ? null : command.getSessionId())
            .turnId(command == null ? null : command.getTurnId())
            .runId(command == null ? null : command.getRunId())
            .traceId(command == null ? null : command.getTraceId())
            .currentUserMessageId(command == null ? null : command.getCurrentUserMessageId())
            .assistantMessageId(command == null ? null : command.getAssistantMessageId())
            .workingContextSnapshotId(command == null ? null : command.getWorkingContextSnapshotId())
            .agentMode(resolveAgentMode(command))
            .build();
    }

    private AgentMode resolveAgentMode(SessionMemoryUpdateCommand command) {
        return command == null || command.getAgentMode() == null ? AgentMode.GENERAL : command.getAgentMode();
    }

    private SessionStateSnapshot emptyState(SessionMemoryUpdateCommand command) {
        return SessionStateSnapshot.builder()
            .snapshotId("state-" + UUID.randomUUID())
            .sessionId(command == null ? null : command.getSessionId())
            .stateVersion(0L)
            .updatedAt(Instant.now())
            .build();
    }

    private SessionStateSnapshot copyAsNextState(SessionStateSnapshot baseState, SessionStateSnapshot merged) {
        long nextVersion = baseState == null || baseState.getStateVersion() == null ? 1L : baseState.getStateVersion() + 1;
        return SessionStateSnapshot.builder()
            .snapshotId("state-" + UUID.randomUUID())
            .sessionId(merged.getSessionId())
            .stateVersion(nextVersion)
            .taskGoal(merged.getTaskGoal())
            .confirmedFacts(merged.getConfirmedFacts())
            .constraints(merged.getConstraints())
            .userPreferences(merged.getUserPreferences())
            .decisions(merged.getDecisions())
            .openLoops(merged.getOpenLoops())
            .recentToolOutcomes(merged.getRecentToolOutcomes())
            .workingMode(merged.getWorkingMode())
            .phaseState(merged.getPhaseState())
            .updatedAt(Instant.now())
            .build();
    }

    private String refreshStateCache(SessionStateSnapshot snapshot) {
        try {
            sessionStateCacheRepository.save(snapshot);
            return null;
        } catch (Exception ex) {
            log.warn("Redis state snapshot refresh failed, sessionId={}", snapshot == null ? null : snapshot.getSessionId(), ex);
            return "Redis state snapshot refresh failed: " + ex.getMessage();
        }
    }

    private String refreshSummaryCache(ConversationSummary summary) {
        try {
            sessionSummaryCacheRepository.save(summary);
            return null;
        } catch (Exception ex) {
            log.warn("Redis summary snapshot refresh failed, sessionId={}", summary == null ? null : summary.getSessionId(), ex);
            return "Redis summary snapshot refresh failed: " + ex.getMessage();
        }
    }

    private void collectFailure(List<String> failureReasons, String failureReason) {
        if (failureReason != null && !failureReason.isBlank()) {
            failureReasons.add(failureReason);
        }
    }

    private record MemoryUpdatePartial(String taskId, Long newVersion, boolean degraded, String failureReason) {

        private static MemoryUpdatePartial empty() {
            return new MemoryUpdatePartial(null, null, false, null);
        }

        private static MemoryUpdatePartial success(String taskId, Long newVersion) {
            return new MemoryUpdatePartial(taskId, newVersion, false, null);
        }

        private static MemoryUpdatePartial degraded(String taskId, String failureReason) {
            return new MemoryUpdatePartial(taskId, null, true, failureReason);
        }

        private static MemoryUpdatePartial degraded(String taskId, Long newVersion, String failureReason) {
            return new MemoryUpdatePartial(taskId, newVersion, true, failureReason);
        }
    }
}
