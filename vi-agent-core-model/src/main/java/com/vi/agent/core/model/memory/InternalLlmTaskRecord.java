package com.vi.agent.core.model.memory;

import com.vi.agent.core.model.context.CheckpointTrigger;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 内部 LLM 任务审计记录。
 */
@Value
@Builder
@Jacksonized
public class InternalLlmTaskRecord {

    /** 内部任务 ID。 */
    String internalTaskId;

    /** 任务类型。 */
    InternalTaskType taskType;

    /** session ID。 */
    String sessionId;

    /** turn ID。 */
    String turnId;

    /** run ID。 */
    String runId;

    /** 触发本次任务的 checkpoint trigger。 */
    CheckpointTrigger checkpointTrigger;

    /** 模板 key。 */
    String promptTemplateKey;

    /** 模板版本。 */
    String promptTemplateVersion;

    /** 内部任务请求 JSON。 */
    String requestJson;

    /** 内部任务响应 JSON。 */
    String responseJson;

    /** 任务状态。 */
    InternalTaskStatus status;

    /** 错误码。 */
    String errorCode;

    /** 错误消息。 */
    String errorMessage;

    /** 执行耗时。 */
    Long durationMs;

    /** 创建时间。 */
    Instant createdAt;

    /** 完成时间。 */
    Instant completedAt;
}
