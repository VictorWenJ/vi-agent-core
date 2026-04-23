package com.vi.agent.core.infra.persistence.message.model;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Aggregated rows for one message.
 */
@Getter
@Builder
public class MessageAggregateRows {

    private AgentMessageEntity message;

    private List<AgentMessageToolCallEntity> toolCalls;

    private AgentToolExecutionEntity toolExecution;
}