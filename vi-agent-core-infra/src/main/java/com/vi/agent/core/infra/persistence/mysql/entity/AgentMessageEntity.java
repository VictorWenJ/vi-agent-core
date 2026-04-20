package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message")
public class AgentMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String role;

    private String messageType;

    private Long sequenceNo;

    private String content;

    private LocalDateTime createdAt;
}
