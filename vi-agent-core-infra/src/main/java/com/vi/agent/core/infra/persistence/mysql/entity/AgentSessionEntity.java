package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_session")
public class AgentSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String conversationId;

    private String parentSessionId;

    private String status;

    private String archiveReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime archivedAt;
}
