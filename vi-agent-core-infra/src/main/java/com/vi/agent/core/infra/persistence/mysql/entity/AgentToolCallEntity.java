package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_call")
/**
 * 模型发起的工具调用事实的 MySQL 实体。
 */
public class AgentToolCallEntity {

    /** MySQL 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 运行时生成的工具调用标识。 */
    private String toolCallId;

    /** 归属的 conversation 标识。 */
    private String conversationId;

    /** 归属的 session 标识。 */
    private String sessionId;

    /** 归属的 turn 标识。 */
    private String turnId;

    /** 承载该工具调用的助手消息标识。 */
    private String messageId;

    /** 模型请求调用的工具名称。 */
    private String toolName;

    /** 传给工具的 JSON 参数。 */
    private String argumentsJson;

    /** turn 内工具调用顺序号。 */
    private Integer sequenceNo;

    /** 工具调用状态码。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
