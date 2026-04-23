package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.runtime.RunEventActorType;
import com.vi.agent.core.model.runtime.RunEventType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Run event persistence entity.
 */
@Data
@TableName("agent_run_event")
public class AgentRunEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String runId;

    private Integer eventIndex;

    private RunEventType eventType;

    private RunEventActorType actorType;

    private String actorId;

    private String payloadJson;

    private LocalDateTime createdAt;
}
