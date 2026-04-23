package com.vi.agent.core.infra.persistence.message.handler;

import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;

import java.util.List;

/**
 * Message type handler.
 */
public interface MessageTypeHandler<T extends Message> {

    MessageRole role();

    List<MessageType> supportedTypes();

    T assemble(MessageAggregateRows rows);

    MessageWritePlan decompose(T message);
}
