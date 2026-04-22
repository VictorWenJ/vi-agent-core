package com.vi.agent.core.infra.persistence.mysql.message;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息处理器注册表。
 */
@Component
public class MessageTypeHandlerRegistry {

    private final Map<MessageTypeKey, MessageTypeHandler<? extends Message>> handlers = new HashMap<>();

    @Resource
    public void registerHandlers(List<MessageTypeHandler<? extends Message>> messageTypeHandlers) {
        handlers.clear();
        for (MessageTypeHandler<? extends Message> handler : messageTypeHandlers) {
            for (MessageType messageType : handler.supportedTypes()) {
                handlers.put(new MessageTypeKey(handler.role(), messageType), handler);
            }
        }
    }

    public Message assemble(MessageAggregateRows rows) {
        MessageTypeHandler<? extends Message> handler = get(rows.getMessage().getRole(), rows.getMessage().getMessageType());
        return handler.assemble(rows);
    }

    @SuppressWarnings("unchecked")
    public MessageWritePlan decompose(Message message) {
        MessageTypeHandler<Message> handler = (MessageTypeHandler<Message>) get(message.getRole(), message.getMessageType());
        return handler.decompose(message);
    }

    public MessageTypeHandler<? extends Message> get(MessageRole role, MessageType messageType) {
        MessageTypeHandler<? extends Message> handler = handlers.get(new MessageTypeKey(role, messageType));
        if (handler == null) {
            throw new AgentRuntimeException(
                ErrorCode.INVALID_ARGUMENT,
                "No MessageTypeHandler found for role=" + role + ", messageType=" + messageType
            );
        }
        return handler;
    }
}
