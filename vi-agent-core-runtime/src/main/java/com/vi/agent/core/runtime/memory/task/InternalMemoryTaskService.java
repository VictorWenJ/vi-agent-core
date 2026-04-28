package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.common.id.InternalTaskIdGenerator;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.memory.InternalLlmTaskRecord;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.port.InternalLlmTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Internal memory task audit service.
 */
@Slf4j
@Service
public class InternalMemoryTaskService {

    /** 内部任务审计仓储。 */
    private final InternalLlmTaskRepository internalLlmTaskRepository;

    /** 内部任务 ID 生成器。 */
    private final InternalTaskIdGenerator internalTaskIdGenerator;

    /** 内部任务 prompt key / audit metadata 解析器。 */
    private final InternalTaskPromptResolver internalTaskPromptResolver;

    /**
     * Spring 构造器注入。
     */
    @Autowired
    public InternalMemoryTaskService(
        InternalLlmTaskRepository internalLlmTaskRepository,
        InternalTaskIdGenerator internalTaskIdGenerator,
        InternalTaskPromptResolver internalTaskPromptResolver
    ) {
        this.internalLlmTaskRepository = internalLlmTaskRepository;
        this.internalTaskIdGenerator = Objects.requireNonNull(internalTaskIdGenerator, "internalTaskIdGenerator must not be null");
        this.internalTaskPromptResolver = Objects.requireNonNull(internalTaskPromptResolver, "internalTaskPromptResolver must not be null");
    }

    /**
     * 测试子类使用的默认构造器。
     */
    protected InternalMemoryTaskService() {
        this(null, new InternalTaskIdGenerator(), new InternalTaskPromptResolver(null));
    }

    public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command) {
        return execute(command, (internalTaskId, inputJson) -> runDeterministicTask(command, internalTaskId));
    }

    public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command, InternalMemoryTaskExecutor executor) {
        String internalTaskId = nextInternalTaskId();
        Instant startedAt = Instant.now();
        String inputJson = buildInputJson(command, null, null);

        boolean auditOk = saveAudit(buildRecord(command, internalTaskId, inputJson, null, InternalTaskStatus.PENDING, null, null, null, startedAt, null));
        auditOk = saveAudit(buildRecord(command, internalTaskId, inputJson, null, InternalTaskStatus.RUNNING, null, null, null, startedAt, null)) && auditOk;

        try {
            InternalMemoryTaskExecutor taskExecutor = executor == null
                ? (taskId, taskInputJson) -> runDeterministicTask(command, taskId)
                : executor;
            InternalMemoryTaskResult result = taskExecutor.execute(internalTaskId, inputJson);
            if (result == null) {
                throw new IllegalStateException("internal memory task result must not be null");
            }
            Instant completedAt = Instant.now();
            Long durationMs = Duration.between(startedAt, completedAt).toMillis();
            String finalInputJson = buildInputJson(command, result, result.getFailureReason());
            auditOk = saveAudit(buildRecord(
                command,
                internalTaskId,
                finalInputJson,
                result.getOutputJson(),
                result.getStatus(),
                resolveErrorCode(result),
                result.getFailureReason(),
                durationMs,
                startedAt,
                completedAt
            )) && auditOk;
            if (!auditOk && result.isSuccess()) {
                return result.toBuilder()
                    .degraded(true)
                    .failureReason("internal task audit save failed")
                    .build();
            }
            return result.toBuilder()
                .internalTaskId(internalTaskId)
                .taskType(command == null ? null : command.getTaskType())
                .inputJson(finalInputJson)
                .build();
        } catch (Exception ex) {
            log.warn("Internal memory task failed, taskType={}, sessionId={}, turnId={}",
                command == null ? null : command.getTaskType(),
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            Instant completedAt = Instant.now();
            Long durationMs = Duration.between(startedAt, completedAt).toMillis();
            String outputJson = buildFailureOutputJson(command, ex);
            String finalInputJson = buildInputJson(command, null, ex.getMessage());
            saveAudit(buildRecord(
                command,
                internalTaskId,
                finalInputJson,
                outputJson,
                InternalTaskStatus.FAILED,
                "INTERNAL_MEMORY_TASK_FAILED",
                ex.getMessage(),
                durationMs,
                startedAt,
                completedAt
            ));
            return InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(command == null ? null : command.getTaskType())
                .status(InternalTaskStatus.FAILED)
                .success(false)
                .degraded(true)
                .failureReason(ex.getMessage())
                .inputJson(finalInputJson)
                .outputJson(outputJson)
                .build();
        }
    }

    protected InternalMemoryTaskResult runDeterministicTask(InternalMemoryTaskCommand command, String internalTaskId) {
        if (command == null || command.getTaskType() == null) {
            throw new IllegalArgumentException("internal memory task type is required");
        }
        if (command.getTaskType() == InternalTaskType.STATE_EXTRACT) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("degraded", false);
            output.put("stateDeltaEmpty", true);
            output.put("newStateVersion", null);
            output.put("failureReason", null);
            output.put("sourceCandidateIds", java.util.List.of());
            String outputJson = JsonUtils.toJson(output);
            return InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(command.getTaskType())
                .status(InternalTaskStatus.SUCCEEDED)
                .success(true)
                .stateDelta(StateDelta.builder().build())
                .sourceCandidateIds(java.util.List.of())
                .outputJson(outputJson)
                .build();
        }
        if (command.getTaskType() == InternalTaskType.SUMMARY_EXTRACT) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("degraded", false);
            output.put("skipped", true);
            output.put("summaryUpdated", false);
            output.put("newSummaryVersion", null);
            output.put("failureReason", "summary extract has no executor");
            String outputJson = JsonUtils.toJson(output);
            return InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(command.getTaskType())
                .status(InternalTaskStatus.SKIPPED)
                .success(true)
                .skipped(true)
                .evidenceIds(java.util.List.of())
                .stateEvidenceIds(java.util.List.of())
                .summaryEvidenceIds(java.util.List.of())
                .evidenceSavedCount(0)
                .outputJson(outputJson)
                .build();
        }
        if (command.getTaskType() == InternalTaskType.EVIDENCE_ENRICH) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("degraded", false);
            output.put("skipped", true);
            output.put("evidenceIds", java.util.List.of());
            output.put("savedCount", 0);
            output.put("failureReason", null);
            output.put("stateEvidenceIds", java.util.List.of());
            output.put("summaryEvidenceIds", java.util.List.of());
            String outputJson = JsonUtils.toJson(output);
            return InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(command.getTaskType())
                .status(InternalTaskStatus.SKIPPED)
                .success(true)
                .skipped(true)
                .outputJson(outputJson)
                .build();
        }
        throw new IllegalArgumentException("unsupported internal memory task type: " + command.getTaskType());
    }

    private String buildFailureOutputJson(InternalMemoryTaskCommand command, Exception ex) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (command != null && command.getTaskType() == InternalTaskType.STATE_EXTRACT) {
            output.put("success", false);
            output.put("degraded", true);
            output.put("stateDeltaEmpty", true);
            output.put("newStateVersion", null);
            output.put("failureReason", ex.getMessage());
            output.put("sourceCandidateIds", java.util.List.of());
            return JsonUtils.toJson(output);
        }
        if (command != null && command.getTaskType() == InternalTaskType.SUMMARY_EXTRACT) {
            output.put("success", false);
            output.put("degraded", true);
            output.put("skipped", false);
            output.put("summaryUpdated", false);
            output.put("newSummaryVersion", null);
            output.put("failureReason", ex.getMessage());
            return JsonUtils.toJson(output);
        }
        if (command != null && command.getTaskType() == InternalTaskType.EVIDENCE_ENRICH) {
            output.put("success", false);
            output.put("degraded", true);
            output.put("skipped", false);
            output.put("evidenceIds", java.util.List.of());
            output.put("savedCount", 0);
            output.put("failureReason", ex.getMessage());
            output.put("stateEvidenceIds", java.util.List.of());
            output.put("summaryEvidenceIds", java.util.List.of());
            return JsonUtils.toJson(output);
        }
        output.put("failed", true);
        output.put("message", ex.getMessage());
        return JsonUtils.toJson(output);
    }

    private boolean saveAudit(InternalLlmTaskRecord record) {
        try {
            if (internalLlmTaskRepository != null) {
                internalLlmTaskRepository.save(record);
            }
            return true;
        } catch (Exception ex) {
            log.warn("Save internal memory task audit failed, internalTaskId={}", record == null ? null : record.getInternalTaskId(), ex);
            return false;
        }
    }

    private InternalLlmTaskRecord buildRecord(
        InternalMemoryTaskCommand command,
        String internalTaskId,
        String inputJson,
        String outputJson,
        InternalTaskStatus status,
        String errorCode,
        String errorMessage,
        Long durationMs,
        Instant createdAt,
        Instant completedAt
    ) {
        return InternalLlmTaskRecord.builder()
            .internalTaskId(internalTaskId)
            .taskType(command == null ? null : command.getTaskType())
            .sessionId(command == null ? null : command.getSessionId())
            .turnId(command == null ? null : command.getTurnId())
            .runId(command == null ? null : command.getRunId())
            .checkpointTrigger(CheckpointTrigger.POST_TURN)
            .promptTemplateKey(internalTaskPromptResolver.resolvePromptTemplateKey(command == null ? null : command.getTaskType()))
            .promptTemplateVersion(internalTaskPromptResolver.resolvePromptTemplateVersion(command == null ? null : command.getTaskType()))
            .requestJson(inputJson)
            .responseJson(outputJson)
            .status(status)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .durationMs(durationMs)
            .createdAt(createdAt)
            .completedAt(completedAt)
            .build();
    }

    private String resolveErrorCode(InternalMemoryTaskResult result) {
        if (result == null || result.getStatus() == null) {
            return null;
        }
        if (result.getStatus() == InternalTaskStatus.FAILED) {
            return "INTERNAL_MEMORY_TASK_FAILED";
        }
        if (result.getStatus() == InternalTaskStatus.DEGRADED) {
            return "INTERNAL_MEMORY_TASK_DEGRADED";
        }
        return null;
    }

    private String buildInputJson(
        InternalMemoryTaskCommand command,
        InternalMemoryTaskResult result,
        String failureReason
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("taskType", command == null || command.getTaskType() == null ? null : command.getTaskType().name());
        input.put("conversationId", command == null ? null : command.getConversationId());
        input.put("sessionId", command == null ? null : command.getSessionId());
        input.put("turnId", command == null ? null : command.getTurnId());
        input.put("runId", command == null ? null : command.getRunId());
        input.put("traceId", command == null ? null : command.getTraceId());
        input.put("currentUserMessageId", command == null ? null : command.getCurrentUserMessageId());
        input.put("assistantMessageId", command == null ? null : command.getAssistantMessageId());
        input.put("workingContextSnapshotId", command == null ? null : command.getWorkingContextSnapshotId());
        input.put("agentMode", command == null || command.getAgentMode() == null ? null : command.getAgentMode().name());
        input.put("messageIds", command == null ? null : command.getMessageIds());
        input.put("currentStateVersion", command == null ? null : command.getCurrentStateVersion());
        input.put("latestStateVersion", command == null ? null : command.getLatestStateVersion());
        input.put("latestSummaryVersion", command == null ? null : command.getLatestSummaryVersion());
        input.put("stateTaskId", command == null ? null : command.getStateTaskId());
        input.put("summaryTaskId", command == null ? null : command.getSummaryTaskId());
        input.put("stateUpdated", command == null ? null : command.getStateUpdated());
        input.put("summaryUpdated", command == null ? null : command.getSummaryUpdated());
        input.put("sourceCandidateIds", command == null ? null : command.getSourceCandidateIds());
        input.put("promptAudit", internalTaskPromptResolver.promptAudit(
            command == null ? null : command.getTaskType(),
            result == null ? null : result.getPromptRenderMetadata(),
            result == null ? null : result.getStructuredOutputChannelResult(),
            failureReason
        ));
        return JsonUtils.toJson(input);
    }

    private String nextInternalTaskId() {
        return internalTaskIdGenerator.nextId();
    }
}
