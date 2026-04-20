package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_conversation")
public class AgentConversationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String title;

    private String status;

    private String activeSessionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastMessageAt;
}
