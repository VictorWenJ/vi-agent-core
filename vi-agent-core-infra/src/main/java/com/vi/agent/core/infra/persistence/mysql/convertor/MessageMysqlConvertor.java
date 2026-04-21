package com.vi.agent.core.infra.persistence.mysql.convertor;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.model.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class MessageMysqlConvertor {

    public AgentMessageEntity toEntity(String conversationId, String sessionId, Message message) {
        log.info("MessageMysqlConvertor toEntity message:{}", JsonUtils.toJson(message));
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setMessageId(message.getMessageId());
        entity.setConversationId(conversationId);
        entity.setSessionId(sessionId);
        entity.setTurnId(message.getTurnId());
        entity.setRole(message.getRole().name());
        entity.setMessageType(message.getMessageType().name());
        entity.setSequenceNo(message.getSequenceNo());
        entity.setContent(message.getContent());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));
        log.info("MessageMysqlConvertor toEntity entity:{}", JsonUtils.toJson(entity));
        return entity;
    }

    public Message toModel(AgentMessageEntity entity) {
        MessageType messageType = MessageType.valueOf(entity.getMessageType());
        Instant createdAt = MysqlTimeConvertor.toInstant(entity.getCreatedAt());
        long sequenceNo = entity.getSequenceNo() == null ? 0L : entity.getSequenceNo();
        return switch (messageType) {
            case USER_INPUT -> UserMessage.restore(entity.getMessageId(), entity.getTurnId(), sequenceNo, entity.getContent(), createdAt);
            case ASSISTANT_OUTPUT -> AssistantMessage.restore(entity.getMessageId(), entity.getTurnId(), sequenceNo, entity.getContent(), List.of(), createdAt);
            case TOOL_CALL -> ToolCallMessage.restore(entity.getMessageId(), entity.getTurnId(), sequenceNo, entity.getMessageId(), "tool", entity.getContent(), createdAt);
            case TOOL_RESULT -> ToolResultMessage.restore(entity.getMessageId(), entity.getTurnId(), sequenceNo, entity.getMessageId(), "tool", true, entity.getContent(), null, null, null, createdAt);
            case SYSTEM_MESSAGE, SUMMARY_MESSAGE -> AssistantMessage.restore(entity.getMessageId(), entity.getTurnId(), sequenceNo, entity.getContent(), List.of(), createdAt);
        };
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
