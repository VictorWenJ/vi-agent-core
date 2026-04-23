package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具执行事实实体。
 */
@Data
@TableName("agent_tool_execution")
public class AgentToolExecutionEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具执行记录ID。 */
    private String toolExecutionId;

    /** 工具调用记录ID。 */
    private String toolCallRecordId;

    /** provider tool_call_id。 */
    private String toolCallId;

    /** 工具结果消息ID。 */
    private String toolResultMessageId;

    /** 会话窗口ID。 */
    private String conversationId;

    /** Session ID。 */
    private String sessionId;

    /** Turn ID。 */
    private String turnId;

    /** Run ID。 */
    private String runId;

    /** 工具名称。 */
    private String toolName;

    /** 参数JSON。 */
    private String argumentsJson;

    /** 文本输出。 */
    private String outputText;

    /** JSON输出。 */
    private String outputJson;

    /** 执行状态。 */
    private ToolExecutionStatus status;

    /** 错误码。 */
    private String errorCode;

    /** 错误信息。 */
    private String errorMessage;

    /** 执行耗时毫秒。 */
    private Long durationMs;

    /** 开始时间。 */
    private LocalDateTime startedAt;

    /** 完成时间。 */
    private LocalDateTime completedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
