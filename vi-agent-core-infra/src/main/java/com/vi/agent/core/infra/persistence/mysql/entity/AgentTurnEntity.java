package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.turn.TurnStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Turn 持久化实体。
 */
@Data
@TableName("agent_turn")
public class AgentTurnEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Turn ID。 */
    private String turnId;

    /** 会话窗口ID。 */
    private String conversationId;

    /** Session ID。 */
    private String sessionId;

    /** 幂等请求ID。 */
    private String requestId;

    /** Run ID。 */
    private String runId;

    /** Turn状态。 */
    private TurnStatus status;

    /** 用户消息ID。 */
    private String userMessageId;

    /** 助手消息ID。 */
    private String assistantMessageId;

    /** 完成原因。 */
    private FinishReason finishReason;

    /** 输入Token数。 */
    private Integer inputTokens;

    /** 输出Token数。 */
    private Integer outputTokens;

    /** 总Token数。 */
    private Integer totalTokens;

    /** Provider标识。 */
    private String provider;

    /** 模型标识。 */
    private String model;

    /** 错误码。 */
    private String errorCode;

    /** 错误信息。 */
    private String errorMessage;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 完成时间。 */
    private LocalDateTime completedAt;
}
