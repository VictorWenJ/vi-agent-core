package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.tool.ToolCallStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 助手工具调用子结构实体。
 */
@Data
@TableName("agent_message_tool_call")
public class AgentMessageToolCallEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具调用记录ID。 */
    private String toolCallRecordId;

    /** provider tool_call_id。 */
    private String toolCallId;

    /** 助手消息ID。 */
    private String assistantMessageId;

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

    /** 调用顺序索引。 */
    private Integer callIndex;

    /** 调用状态。 */
    private ToolCallStatus status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
