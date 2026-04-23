package com.vi.agent.core.infra.persistence.message.model;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Write plan for one message aggregate.
 */
@Getter
@Builder
public class MessageWritePlan {

    private AgentMessageEntity message;

    private List<AgentMessageToolCallEntity> toolCalls;

    private AgentToolExecutionEntity toolExecution;
}