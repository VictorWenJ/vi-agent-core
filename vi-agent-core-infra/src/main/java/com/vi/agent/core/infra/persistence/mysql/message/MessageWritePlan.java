package com.vi.agent.core.infra.persistence.mysql.message;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 消息写入计划。
 */
@Getter
@Builder
public class MessageWritePlan {

    private AgentMessageEntity message;

    private List<AgentMessageToolCallEntity> toolCalls;

    private AgentToolExecutionEntity toolExecution;
}
