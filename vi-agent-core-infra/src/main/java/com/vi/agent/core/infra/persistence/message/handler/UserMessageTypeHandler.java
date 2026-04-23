package com.vi.agent.core.infra.persistence.message.handler;

import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.UserMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * UserMessage handler.
 */
@Component
public class UserMessageTypeHandler implements MessageTypeHandler<UserMessage> {

    @Override
    public MessageRole role() {
        return MessageRole.USER;
    }

    @Override
    public List<MessageType> supportedTypes() {
        return List.of(MessageType.USER_INPUT);
    }

    @Override
    public UserMessage assemble(MessageAggregateRows rows) {
        AgentMessageEntity entity = rows.getMessage();
        return UserMessage.restore(
            entity.getMessageId(),
            entity.getConversationId(),
            entity.getSessionId(),
            entity.getTurnId(),
            entity.getRunId(),
            entity.getSequenceNo(),
            entity.getStatus(),
            entity.getContentText(),
            MysqlTimeConvertor.toInstant(entity.getCreatedAt())
        );
    }

    @Override
    public MessageWritePlan decompose(UserMessage message) {
        Instant createdAt = defaultNow(message.getCreatedAt());
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setMessageId(message.getMessageId());
        entity.setConversationId(message.getConversationId());
        entity.setSessionId(message.getSessionId());
        entity.setTurnId(message.getTurnId());
        entity.setRunId(message.getRunId());
        entity.setRole(message.getRole());
        entity.setMessageType(message.getMessageType());
        entity.setSequenceNo(message.getSequenceNo());
        entity.setStatus(message.getStatus());
        entity.setContentText(message.getContentText());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(createdAt));
        entity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(createdAt));
        return MessageWritePlan.builder().message(entity).build();
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
