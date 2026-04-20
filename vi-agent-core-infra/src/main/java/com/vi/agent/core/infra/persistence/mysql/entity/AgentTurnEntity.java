package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_turn")
/**
 * turn 级执行事实与完成元数据的 MySQL 实体。
 */
public class AgentTurnEntity {

    /** MySQL 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一 turn 标识。 */
    private String turnId;

    /** 归属的 conversation 标识。 */
    private String conversationId;

    /** 归属的 session 标识。 */
    private String sessionId;

    /** 客户端请求标识（用于幂等）。 */
    private String requestId;

    /** 与该 turn 绑定的 run 标识。 */
    private String runId;

    /** turn 状态码，例如 RUNNING/COMPLETED/FAILED。 */
    private String status;

    /** 本 turn 生成的用户消息标识。 */
    private String userMessageId;

    /** 本 turn 生成的助手消息标识。 */
    private String assistantMessageId;

    /** 模型/运行时返回的完成原因编码。 */
    private String finishReason;

    /** 本 turn 聚合输入 token 数。 */
    private Integer inputTokens;

    /** 本 turn 聚合输出 token 数。 */
    private Integer outputTokens;

    /** 本 turn 聚合总 token 数。 */
    private Integer totalTokens;

    /** 本 turn 使用的 provider 名称。 */
    private String provider;

    /** 本 turn 使用的模型名称。 */
    private String model;

    /** turn 执行失败时的错误码。 */
    private String errorCode;

    /** turn 执行失败时的错误信息。 */
    private String errorMessage;

    /** turn 创建时间。 */
    private LocalDateTime createdAt;

    /** turn 完成时间。 */
    private LocalDateTime completedAt;
}
