package com.vi.agent.core.infra.persistence.message.handler;

import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.SystemMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * SystemMessage handler.
 */
@Component
public class SystemMessageTypeHandler implements MessageTypeHandler<SystemMessage> {

    @Override
    public MessageRole role() {
        return MessageRole.SYSTEM;
    }

    @Override
    public List<MessageType> supportedTypes() {
        return List.of(MessageType.SYSTEM_PROMPT);
    }

    @Override
    public SystemMessage assemble(MessageAggregateRows rows) {
        AgentMessageEntity entity = rows.getMessage();
        return SystemMessage.restore(
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
    public MessageWritePlan decompose(SystemMessage message) {
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
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));
        entity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));
        return MessageWritePlan.builder().message(entity).build();
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
