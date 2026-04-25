package com.vi.agent.core.runtime.memory;

import com.vi.agent.core.common.id.ConversationSummaryIdGenerator;
import com.vi.agent.core.common.id.SessionStateSnapshotIdGenerator;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MemoryEvidenceRepository;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionStateCacheRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.SessionSummaryCacheRepository;
import com.vi.agent.core.model.port.SessionSummaryRepository;
import com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractionCommand;
import com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractionResult;
import com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractor;
import com.vi.agent.core.runtime.memory.extract.StateDeltaExtractionCommand;
import com.vi.agent.core.runtime.memory.extract.StateDeltaExtractionResult;
import com.vi.agent.core.runtime.memory.extract.StateDeltaExtractor;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskCommand;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskResult;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private MessageRepository messageRepository;

    @Resource
    private StateDeltaExtractor stateDeltaExtractor;

    /** 会话摘要抽取器。 */
    @Resource
    private ConversationSummaryExtractor conversationSummaryExtractor;

    @Resource
    private InternalMemoryTaskService internalMemoryTaskService;

    @Resource
    private StateDeltaMerger stateDeltaMerger;

    @Resource
    private SessionStateSnapshotIdGenerator sessionStateSnapshotIdGenerator;

    /** 会话摘要 ID 生成器。 */
    @Resource
    private ConversationSummaryIdGenerator conversationSummaryIdGenerator;

    public SessionMemoryUpdateResult updateAfterTurn(SessionMemoryUpdateCommand command) {
        if (properties != null && !properties.isPostTurnUpdateEnabled()) {
            return SessionMemoryUpdateResult.builder()
                .success(true)
                .skipped(true)
                .build();
        }
        if (properties != null && !properties.isStateExtractionEnabled() && !properties.isSummaryUpdateEnabled()) {
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
            Optional<ConversationSummary> latestSummary = sessionSummaryRepository.findLatestBySessionId(command.getSessionId());
            List<Message> turnMessages = loadCompletedTurnMessages(command);
            InternalMemoryTaskResult taskResult = internalMemoryTaskService.execute(
                toStateTaskCommand(command, latestState.orElse(null), turnMessages),
                (internalTaskId, inputJson) -> executeStateExtractTask(
                    command,
                    internalTaskId,
                    latestState.orElse(null),
                    latestSummary.orElse(null),
                    turnMessages
                )
            );
            if (taskResult.isDegraded()) {
                return MemoryUpdatePartial.degraded(
                    taskResult.getInternalTaskId(),
                    taskResult.getNewStateVersion(),
                    taskResult.getFailureReason()
                );
            }
            if (!taskResult.isSuccess()) {
                return MemoryUpdatePartial.degraded(taskResult.getInternalTaskId(), taskResult.getFailureReason());
            }
            return MemoryUpdatePartial.success(taskResult.getInternalTaskId(), taskResult.getNewStateVersion());
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
            Optional<ConversationSummary> latestSummary = sessionSummaryRepository.findLatestBySessionId(command.getSessionId());
            Optional<SessionStateSnapshot> latestState = sessionStateRepository.findLatestBySessionId(command.getSessionId());
            List<Message> turnMessages = loadCompletedTurnMessages(command);
            InternalMemoryTaskResult taskResult = internalMemoryTaskService.execute(
                toSummaryTaskCommand(command, latestSummary.orElse(null), latestState.orElse(null), turnMessages),
                (internalTaskId, inputJson) -> executeSummaryExtractTask(
                    command,
                    internalTaskId,
                    latestSummary.orElse(null),
                    latestState.orElse(null),
                    turnMessages
                )
            );
            if (taskResult.isDegraded()) {
                return MemoryUpdatePartial.degraded(
                    taskResult.getInternalTaskId(),
                    taskResult.getNewSummaryVersion(),
                    taskResult.getFailureReason()
                );
            }
            if (!taskResult.isSuccess()) {
                return MemoryUpdatePartial.degraded(taskResult.getInternalTaskId(), taskResult.getFailureReason());
            }
            if (taskResult.isSkipped()) {
                return MemoryUpdatePartial.success(taskResult.getInternalTaskId(), null);
            }
            return MemoryUpdatePartial.success(taskResult.getInternalTaskId(), taskResult.getNewSummaryVersion());
        } catch (Exception ex) {
            log.warn("Post-turn summary memory update failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return MemoryUpdatePartial.degraded(null, ex.getMessage());
        }
    }

    private InternalMemoryTaskCommand toStateTaskCommand(SessionMemoryUpdateCommand command, SessionStateSnapshot latestState, List<Message> turnMessages) {
        InternalMemoryTaskCommand.InternalMemoryTaskCommandBuilder builder = InternalMemoryTaskCommand.builder()
            .taskType(InternalTaskType.STATE_EXTRACT)
            .conversationId(command == null ? null : command.getConversationId())
            .sessionId(command == null ? null : command.getSessionId())
            .turnId(command == null ? null : command.getTurnId())
            .runId(command == null ? null : command.getRunId())
            .traceId(command == null ? null : command.getTraceId())
            .currentUserMessageId(command == null ? null : command.getCurrentUserMessageId())
            .assistantMessageId(command == null ? null : command.getAssistantMessageId())
            .workingContextSnapshotId(command == null ? null : command.getWorkingContextSnapshotId())
            .agentMode(resolveAgentMode(command))
            .currentStateVersion(latestState == null ? null : latestState.getStateVersion())
            .latestStateVersion(latestState == null ? null : latestState.getStateVersion());
        for (Message message : nullSafeMessages(turnMessages)) {
            builder.messageId(message.getMessageId());
        }
        return builder.build();
    }

    private InternalMemoryTaskCommand toSummaryTaskCommand(
        SessionMemoryUpdateCommand command,
        ConversationSummary latestSummary,
        SessionStateSnapshot latestState,
        List<Message> turnMessages
    ) {
        InternalMemoryTaskCommand.InternalMemoryTaskCommandBuilder builder = InternalMemoryTaskCommand.builder()
            .taskType(InternalTaskType.SUMMARY_EXTRACT)
            .conversationId(command == null ? null : command.getConversationId())
            .sessionId(command == null ? null : command.getSessionId())
            .turnId(command == null ? null : command.getTurnId())
            .runId(command == null ? null : command.getRunId())
            .traceId(command == null ? null : command.getTraceId())
            .currentUserMessageId(command == null ? null : command.getCurrentUserMessageId())
            .assistantMessageId(command == null ? null : command.getAssistantMessageId())
            .workingContextSnapshotId(command == null ? null : command.getWorkingContextSnapshotId())
            .agentMode(resolveAgentMode(command))
            .latestSummaryVersion(latestSummary == null ? null : latestSummary.getSummaryVersion())
            .latestStateVersion(latestState == null ? null : latestState.getStateVersion());
        for (Message message : nullSafeMessages(turnMessages)) {
            builder.messageId(message.getMessageId());
        }
        return builder.build();
    }

    private List<Message> loadCompletedTurnMessages(SessionMemoryUpdateCommand command) {
        if (messageRepository == null || command == null) {
            return List.of();
        }
        return messageRepository.findCompletedMessagesByTurnId(command.getTurnId());
    }

    private InternalMemoryTaskResult executeStateExtractTask(
        SessionMemoryUpdateCommand command,
        String internalTaskId,
        SessionStateSnapshot latestState,
        ConversationSummary latestSummary,
        List<Message> turnMessages
    ) {
        try {
            StateDeltaExtractionResult extractionResult = extractStateDelta(command, latestState, latestSummary, turnMessages);
            StateDelta stateDelta = extractionResult == null ? null : extractionResult.getStateDelta();
            List<String> sourceCandidateIds = sourceCandidateIds(extractionResult, stateDelta);
            if (extractionResult == null || !extractionResult.isSuccess() || extractionResult.isDegraded()) {
                String failureReason = extractionResult == null
                    ? "state delta extraction returned null result"
                    : extractionResult.getFailureReason();
                return stateTaskResult(
                    internalTaskId,
                    InternalTaskStatus.DEGRADED,
                    false,
                    true,
                    stateDelta,
                    null,
                    failureReason,
                    sourceCandidateIds
                );
            }
            if (stateDelta == null || stateDelta.isEmpty()) {
                return stateTaskResult(
                    internalTaskId,
                    InternalTaskStatus.SUCCEEDED,
                    true,
                    false,
                    stateDelta == null ? StateDelta.builder().build() : stateDelta,
                    null,
                    null,
                    sourceCandidateIds
                );
            }

            SessionStateSnapshot baseState = latestState == null ? emptyState(command) : latestState;
            SessionStateSnapshot merged = stateDeltaMerger.merge(baseState, stateDelta);
            SessionStateSnapshot nextState = copyAsNextState(baseState, merged);
            try {
                sessionStateRepository.save(nextState);
            } catch (Exception ex) {
                log.warn("Post-turn state save failed, sessionId={}, turnId={}",
                    command == null ? null : command.getSessionId(),
                    command == null ? null : command.getTurnId(),
                    ex);
                return stateTaskResult(
                    internalTaskId,
                    InternalTaskStatus.FAILED,
                    false,
                    true,
                    stateDelta,
                    null,
                    ex.getMessage(),
                    sourceCandidateIds
                );
            }

            String redisFailure = refreshStateCache(nextState);
            if (redisFailure != null) {
                return stateTaskResult(
                    internalTaskId,
                    InternalTaskStatus.DEGRADED,
                    false,
                    true,
                    stateDelta,
                    nextState.getStateVersion(),
                    redisFailure,
                    sourceCandidateIds
                );
            }
            return stateTaskResult(
                internalTaskId,
                InternalTaskStatus.SUCCEEDED,
                true,
                false,
                stateDelta,
                nextState.getStateVersion(),
                null,
                sourceCandidateIds
            );
        } catch (Exception ex) {
            log.warn("STATE_EXTRACT task execution failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return stateTaskResult(
                internalTaskId,
                InternalTaskStatus.FAILED,
                false,
                true,
                null,
                null,
                ex.getMessage(),
                List.of()
            );
        }
    }

    private StateDeltaExtractionResult extractStateDelta(
        SessionMemoryUpdateCommand command,
        SessionStateSnapshot latestState,
        ConversationSummary latestSummary,
        List<Message> turnMessages
    ) {
        if (stateDeltaExtractor == null) {
            return StateDeltaExtractionResult.builder()
                .success(false)
                .degraded(true)
                .failureReason("state delta extractor is not configured")
                .build();
        }
        return stateDeltaExtractor.extract(StateDeltaExtractionCommand.builder()
            .conversationId(command == null ? null : command.getConversationId())
            .sessionId(command == null ? null : command.getSessionId())
            .turnId(command == null ? null : command.getTurnId())
            .runId(command == null ? null : command.getRunId())
            .traceId(command == null ? null : command.getTraceId())
            .agentMode(resolveAgentMode(command))
            .currentState(latestState)
            .conversationSummary(latestSummary)
            .turnMessages(nullSafeMessages(turnMessages))
            .workingContextSnapshotId(command == null ? null : command.getWorkingContextSnapshotId())
            .build());
    }

    private InternalMemoryTaskResult executeSummaryExtractTask(
        SessionMemoryUpdateCommand command,
        String internalTaskId,
        ConversationSummary latestSummary,
        SessionStateSnapshot latestState,
        List<Message> turnMessages
    ) {
        try {
            ConversationSummaryExtractionResult extractionResult = extractConversationSummary(command, latestSummary, latestState, turnMessages);
            if (extractionResult == null || !extractionResult.isSuccess() || extractionResult.isDegraded()) {
                String failureReason = extractionResult == null
                    ? "summary extraction returned null result"
                    : extractionResult.getFailureReason();
                return summaryTaskResult(
                    internalTaskId,
                    InternalTaskStatus.DEGRADED,
                    false,
                    true,
                    false,
                    null,
                    null,
                    failureReason
                );
            }
            if (extractionResult.isSkipped() || extractionResult.getConversationSummary() == null
                || extractionResult.getConversationSummary().getSummaryText() == null
                || extractionResult.getConversationSummary().getSummaryText().isBlank()) {
                return summaryTaskResult(
                    internalTaskId,
                    InternalTaskStatus.SKIPPED,
                    true,
                    false,
                    true,
                    null,
                    null,
                    extractionResult.getFailureReason()
                );
            }

            ConversationSummary nextSummary = buildNextSummary(command, latestSummary, extractionResult, turnMessages);
            try {
                sessionSummaryRepository.save(nextSummary);
            } catch (Exception ex) {
                log.warn("Post-turn summary save failed, sessionId={}, turnId={}",
                    command == null ? null : command.getSessionId(),
                    command == null ? null : command.getTurnId(),
                    ex);
                return summaryTaskResult(
                    internalTaskId,
                    InternalTaskStatus.FAILED,
                    false,
                    true,
                    false,
                    null,
                    null,
                    ex.getMessage()
                );
            }

            String redisFailure = refreshSummaryCache(nextSummary);
            if (redisFailure != null) {
                return summaryTaskResult(
                    internalTaskId,
                    InternalTaskStatus.DEGRADED,
                    false,
                    true,
                    false,
                    nextSummary,
                    nextSummary.getSummaryVersion(),
                    redisFailure
                );
            }
            return summaryTaskResult(
                internalTaskId,
                InternalTaskStatus.SUCCEEDED,
                true,
                false,
                false,
                nextSummary,
                nextSummary.getSummaryVersion(),
                null
            );
        } catch (Exception ex) {
            log.warn("SUMMARY_EXTRACT task execution failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return summaryTaskResult(
                internalTaskId,
                InternalTaskStatus.FAILED,
                false,
                true,
                false,
                null,
                null,
                ex.getMessage()
            );
        }
    }

    private ConversationSummaryExtractionResult extractConversationSummary(
        SessionMemoryUpdateCommand command,
        ConversationSummary latestSummary,
        SessionStateSnapshot latestState,
        List<Message> turnMessages
    ) {
        if (conversationSummaryExtractor == null) {
            return ConversationSummaryExtractionResult.builder()
                .success(false)
                .degraded(true)
                .failureReason("conversation summary extractor is not configured")
                .build();
        }
        return conversationSummaryExtractor.extract(ConversationSummaryExtractionCommand.builder()
            .conversationId(command == null ? null : command.getConversationId())
            .sessionId(command == null ? null : command.getSessionId())
            .turnId(command == null ? null : command.getTurnId())
            .runId(command == null ? null : command.getRunId())
            .traceId(command == null ? null : command.getTraceId())
            .agentMode(resolveAgentMode(command))
            .latestSummary(latestSummary)
            .latestState(latestState)
            .turnMessages(nullSafeMessages(turnMessages))
            .workingContextSnapshotId(command == null ? null : command.getWorkingContextSnapshotId())
            .build());
    }

    private ConversationSummary buildNextSummary(
        SessionMemoryUpdateCommand command,
        ConversationSummary latestSummary,
        ConversationSummaryExtractionResult extractionResult,
        List<Message> turnMessages
    ) {
        if (conversationSummaryIdGenerator == null) {
            throw new IllegalStateException("conversation summary id generator is not configured");
        }
        long nextVersion = latestSummary == null || latestSummary.getSummaryVersion() == null
            ? 1L
            : latestSummary.getSummaryVersion() + 1;
        Long coveredFromSequenceNo = coveredFromSequenceNo(latestSummary, turnMessages);
        Long coveredToSequenceNo = coveredToSequenceNo(latestSummary, turnMessages);
        if (coveredFromSequenceNo == null || coveredToSequenceNo == null) {
            throw new IllegalStateException("summary coverage sequence range cannot be computed");
        }
        return ConversationSummary.builder()
            .summaryId(conversationSummaryIdGenerator.nextId())
            .sessionId(command == null ? null : command.getSessionId())
            .summaryVersion(nextVersion)
            .coveredFromSequenceNo(coveredFromSequenceNo)
            .coveredToSequenceNo(coveredToSequenceNo)
            .summaryText(extractionResult.getConversationSummary().getSummaryText())
            .summaryTemplateKey(com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractionPromptBuilder.PROMPT_TEMPLATE_KEY)
            .summaryTemplateVersion(com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractionPromptBuilder.PROMPT_TEMPLATE_VERSION)
            .generatorProvider(extractionResult.getGeneratorProvider())
            .generatorModel(extractionResult.getGeneratorModel())
            .createdAt(Instant.now())
            .build();
    }

    private InternalMemoryTaskResult stateTaskResult(
        String internalTaskId,
        InternalTaskStatus status,
        boolean success,
        boolean degraded,
        StateDelta stateDelta,
        Long newStateVersion,
        String failureReason,
        List<String> sourceCandidateIds
    ) {
        String outputJson = buildStateTaskOutputJson(success, degraded, stateDelta, newStateVersion, failureReason, sourceCandidateIds);
        return InternalMemoryTaskResult.builder()
            .internalTaskId(internalTaskId)
            .taskType(InternalTaskType.STATE_EXTRACT)
            .status(status)
            .success(success)
            .degraded(degraded)
            .stateDelta(stateDelta)
            .newStateVersion(newStateVersion)
            .sourceCandidateIds(sourceCandidateIds == null ? List.of() : sourceCandidateIds)
            .failureReason(failureReason)
            .outputJson(outputJson)
            .build();
    }

    private InternalMemoryTaskResult summaryTaskResult(
        String internalTaskId,
        InternalTaskStatus status,
        boolean success,
        boolean degraded,
        boolean skipped,
        ConversationSummary summary,
        Long newSummaryVersion,
        String failureReason
    ) {
        String outputJson = buildSummaryTaskOutputJson(success, degraded, skipped, summary != null, newSummaryVersion, failureReason);
        return InternalMemoryTaskResult.builder()
            .internalTaskId(internalTaskId)
            .taskType(InternalTaskType.SUMMARY_EXTRACT)
            .status(status)
            .success(success)
            .degraded(degraded)
            .skipped(skipped)
            .summary(summary)
            .newSummaryVersion(newSummaryVersion)
            .failureReason(failureReason)
            .outputJson(outputJson)
            .build();
    }

    private String buildSummaryTaskOutputJson(
        boolean success,
        boolean degraded,
        boolean skipped,
        boolean summaryUpdated,
        Long newSummaryVersion,
        String failureReason
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", success);
        output.put("degraded", degraded);
        output.put("skipped", skipped);
        output.put("summaryUpdated", summaryUpdated);
        output.put("newSummaryVersion", newSummaryVersion);
        output.put("failureReason", failureReason);
        return JsonUtils.toJson(output);
    }

    private String buildStateTaskOutputJson(
        boolean success,
        boolean degraded,
        StateDelta stateDelta,
        Long newStateVersion,
        String failureReason,
        List<String> sourceCandidateIds
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", success);
        output.put("degraded", degraded);
        output.put("stateDeltaEmpty", stateDelta == null || stateDelta.isEmpty());
        output.put("newStateVersion", newStateVersion);
        output.put("failureReason", failureReason);
        output.put("sourceCandidateIds", sourceCandidateIds == null ? List.of() : sourceCandidateIds);
        return JsonUtils.toJson(output);
    }

    private List<String> sourceCandidateIds(StateDeltaExtractionResult extractionResult, StateDelta stateDelta) {
        if (extractionResult != null && extractionResult.getSourceCandidateIds() != null) {
            return extractionResult.getSourceCandidateIds();
        }
        if (stateDelta != null && stateDelta.getSourceCandidateIds() != null) {
            return stateDelta.getSourceCandidateIds();
        }
        return List.of();
    }

    private List<Message> nullSafeMessages(List<Message> messages) {
        return messages == null ? List.of() : messages;
    }

    private Long coveredFromSequenceNo(ConversationSummary latestSummary, List<Message> turnMessages) {
        Long minTurnSequence = nullSafeMessages(turnMessages).stream()
            .filter(message -> message != null)
            .map(Message::getSequenceNo)
            .min(Long::compareTo)
            .orElse(null);
        Long latestCoveredFrom = latestSummary == null ? null : latestSummary.getCoveredFromSequenceNo();
        if (latestCoveredFrom == null) {
            return minTurnSequence;
        }
        if (minTurnSequence == null) {
            return latestCoveredFrom;
        }
        return Math.min(latestCoveredFrom, minTurnSequence);
    }

    private Long coveredToSequenceNo(ConversationSummary latestSummary, List<Message> turnMessages) {
        Long maxTurnSequence = nullSafeMessages(turnMessages).stream()
            .filter(message -> message != null)
            .map(Message::getSequenceNo)
            .max(Long::compareTo)
            .orElse(null);
        Long latestCoveredTo = latestSummary == null ? null : latestSummary.getCoveredToSequenceNo();
        if (latestCoveredTo == null) {
            return maxTurnSequence;
        }
        if (maxTurnSequence == null) {
            return latestCoveredTo;
        }
        return Math.max(latestCoveredTo, maxTurnSequence);
    }

    private AgentMode resolveAgentMode(SessionMemoryUpdateCommand command) {
        return command == null || command.getAgentMode() == null ? AgentMode.GENERAL : command.getAgentMode();
    }

    private SessionStateSnapshot emptyState(SessionMemoryUpdateCommand command) {
        return SessionStateSnapshot.builder()
            .snapshotId(sessionStateSnapshotIdGenerator.nextId())
            .sessionId(command == null ? null : command.getSessionId())
            .stateVersion(0L)
            .updatedAt(Instant.now())
            .build();
    }

    private SessionStateSnapshot copyAsNextState(SessionStateSnapshot baseState, SessionStateSnapshot merged) {
        long nextVersion = baseState == null || baseState.getStateVersion() == null ? 1L : baseState.getStateVersion() + 1;
        return SessionStateSnapshot.builder()
            .snapshotId(sessionStateSnapshotIdGenerator.nextId())
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
