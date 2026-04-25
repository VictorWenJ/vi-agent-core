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

    @Resource
    private InternalLlmTaskRepository internalLlmTaskRepository;

    public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command) {
        String internalTaskId = nextInternalTaskId();
        Instant startedAt = Instant.now();
        String inputJson = buildInputJson(command);

        boolean auditOk = saveAudit(buildRecord(command, internalTaskId, inputJson, null, InternalTaskStatus.PENDING, null, null, null, startedAt, null));
        auditOk = saveAudit(buildRecord(command, internalTaskId, inputJson, null, InternalTaskStatus.RUNNING, null, null, null, startedAt, null)) && auditOk;

        try {
            InternalMemoryTaskResult result = runDeterministicTask(command, internalTaskId);
            Instant completedAt = Instant.now();
            Long durationMs = Duration.between(startedAt, completedAt).toMillis();
            auditOk = saveAudit(buildRecord(
                command,
                internalTaskId,
                inputJson,
                result.getOutputJson(),
                result.getStatus(),
                null,
                null,
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
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("failed", true);
            output.put("message", ex.getMessage());
            String outputJson = JsonUtils.toJson(output);
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
        if (command.getTaskType() == InternalTaskType.STATE_EXTRACTION) {
            String outputJson = JsonUtils.toJson(Map.of(
                "noop", true,
                "stateDeltaEmpty", true
            ));
            return InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(command.getTaskType())
                .status(InternalTaskStatus.SUCCEEDED)
                .success(true)
                .stateDelta(StateDelta.builder().build())
                .outputJson(outputJson)
                .build();
        }
        if (command.getTaskType() == InternalTaskType.SUMMARY_UPDATE) {
            String outputJson = JsonUtils.toJson(Map.of(
                "skipped", true,
                "reason", "summary update is deterministic no-op in P2-D-1"
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
            .promptTemplateKey(null)
            .promptTemplateVersion(null)
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
        return JsonUtils.toJson(input);
    }

    private String nextInternalTaskId() {
        return "itask-" + UUID.randomUUID();
    }
}
