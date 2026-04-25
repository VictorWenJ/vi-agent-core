package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.memory.InternalLlmTaskRecord;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.port.InternalLlmTaskRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Internal memory task audit service.
 */
@Slf4j
@Service
public class InternalMemoryTaskService {

    private static final String STATE_EXTRACT_TEMPLATE_KEY = "state_extract_inline";

    private static final String SUMMARY_EXTRACT_TEMPLATE_KEY = "summary_extract_noop";

    private static final String P2_D_2_STATE_TEMPLATE_VERSION = "p2-d-2-v1";

    private static final String P2_D_1_TEMPLATE_VERSION = "p2-d-1-v1";

    private static final String UNKNOWN_TEMPLATE_KEY = "internal_memory_task_noop";

    @Resource
    private InternalLlmTaskRepository internalLlmTaskRepository;

    public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command) {
        return execute(command, (internalTaskId, inputJson) -> runDeterministicTask(command, internalTaskId));
    }

    public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command, InternalMemoryTaskExecutor executor) {
        String internalTaskId = nextInternalTaskId();
        Instant startedAt = Instant.now();
        String inputJson = buildInputJson(command);

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
            auditOk = saveAudit(buildRecord(
                command,
                internalTaskId,
                inputJson,
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
                .inputJson(inputJson)
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
            saveAudit(buildRecord(
                command,
                internalTaskId,
                inputJson,
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
                .inputJson(inputJson)
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
            String outputJson = JsonUtils.toJson(Map.of(
                "skipped", true,
                "reason", "summary extract is deterministic no-op in P2-D-1"
            ));
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
            .promptTemplateKey(resolvePromptTemplateKey(command == null ? null : command.getTaskType()))
            .promptTemplateVersion(resolvePromptTemplateVersion(command == null ? null : command.getTaskType()))
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

    private String resolvePromptTemplateKey(InternalTaskType taskType) {
        if (taskType == InternalTaskType.STATE_EXTRACT) {
            return STATE_EXTRACT_TEMPLATE_KEY;
        }
        if (taskType == InternalTaskType.SUMMARY_EXTRACT) {
            return SUMMARY_EXTRACT_TEMPLATE_KEY;
        }
        return UNKNOWN_TEMPLATE_KEY;
    }

    private String resolvePromptTemplateVersion(InternalTaskType taskType) {
        if (taskType == InternalTaskType.STATE_EXTRACT) {
            return P2_D_2_STATE_TEMPLATE_VERSION;
        }
        return P2_D_1_TEMPLATE_VERSION;
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

    private String buildInputJson(InternalMemoryTaskCommand command) {
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
        return JsonUtils.toJson(input);
    }

    private String nextInternalTaskId() {
        return "itask-" + UUID.randomUUID();
    }
}
