package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_turn")
public class AgentTurnEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String turnId;

    private String conversationId;

    private String sessionId;

    private String requestId;

    private String runId;

    private String status;

    private String userMessageId;

    private String assistantMessageId;

    private String finishReason;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private String provider;

    private String model;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
