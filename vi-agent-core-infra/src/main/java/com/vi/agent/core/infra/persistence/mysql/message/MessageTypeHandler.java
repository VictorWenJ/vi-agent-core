package com.vi.agent.core.infra.persistence.mysql.message;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;

import java.util.List;

/**
 * 消息类型处理器。
 */
public interface MessageTypeHandler<T extends Message> {

    MessageRole role();

    List<MessageType> supportedTypes();

    T assemble(MessageAggregateRows rows);

    MessageWritePlan decompose(T message);
}
