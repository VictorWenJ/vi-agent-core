package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_call")
public class AgentToolCallEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolCallId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String messageId;

    private String toolName;

    private String argumentsJson;

    private Integer sequenceNo;

    private String status;

    private LocalDateTime createdAt;
}
