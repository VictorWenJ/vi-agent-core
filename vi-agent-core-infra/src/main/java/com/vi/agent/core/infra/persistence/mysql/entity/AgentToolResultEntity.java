package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_result")
public class AgentToolResultEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolCallId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String messageId;

    private Boolean success;

    private String toolName;

    private String outputJson;

    private String errorCode;

    private String errorMessage;

    private Long durationMs;

    private LocalDateTime createdAt;
}
