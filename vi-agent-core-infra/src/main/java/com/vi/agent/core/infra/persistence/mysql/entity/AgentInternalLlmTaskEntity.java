package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内部 LLM 任务审计持久化实体。
 */
@Data
@TableName("agent_internal_llm_task")
public class AgentInternalLlmTaskEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内部任务 ID。 */
    private String internalTaskId;

    /** 任务类型。 */
    private String taskType;

    /** session ID。 */
    private String sessionId;

    /** turn ID。 */
    private String turnId;

    /** run ID。 */
    private String runId;

    /** 触发本次任务的 checkpoint trigger。 */
    private String checkpointTrigger;

    /** 模板 key。 */
    private String promptTemplateKey;

    /** 模板版本。 */
    private String promptTemplateVersion;

    /** 内部任务请求 JSON。 */
    private String requestJson;

    /** 内部任务响应 JSON。 */
    private String responseJson;

    /** PENDING/RUNNING/SUCCEEDED/FAILED/DEGRADED/SKIPPED。 */
    private String status;

    /** 错误码。 */
    private String errorCode;

    /** 错误消息。 */
    private String errorMessage;

    /** 执行耗时。 */
    private Long durationMs;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 完成时间。 */
    private LocalDateTime completedAt;
}
