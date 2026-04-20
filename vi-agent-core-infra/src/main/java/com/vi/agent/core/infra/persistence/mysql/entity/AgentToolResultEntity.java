package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_result")
/**
 * 工具执行结果事实的 MySQL 实体。
 */
public class AgentToolResultEntity {

    /** MySQL 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属的工具调用标识。 */
    private String toolCallId;

    /** 归属的 conversation 标识。 */
    private String conversationId;

    /** 归属的 session 标识。 */
    private String sessionId;

    /** 归属的 turn 标识。 */
    private String turnId;

    /** 关联消息标识。 */
    private String messageId;

    /** 工具执行是否成功。 */
    private Boolean success;

    /** 执行的工具名称。 */
    private String toolName;

    /** 成功执行时的 JSON 输出。 */
    private String outputJson;

    /** 执行失败时的错误码。 */
    private String errorCode;

    /** 执行失败时的错误信息。 */
    private String errorMessage;

    /** 工具执行耗时（毫秒）。 */
    private Long durationMs;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
