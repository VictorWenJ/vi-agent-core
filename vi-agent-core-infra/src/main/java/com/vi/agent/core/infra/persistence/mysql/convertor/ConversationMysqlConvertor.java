package com.vi.agent.core.infra.persistence.mysql.convertor;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentConversationEntity;
import com.vi.agent.core.model.conversation.Conversation;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ConversationMysqlConvertor {

    public Conversation toModel(AgentConversationEntity entity) {
        if (entity == null) {
            return null;
        }
        return Conversation.builder()
            .conversationId(entity.getConversationId())
            .title(entity.getTitle())
            .status(entity.getStatus())
            .activeSessionId(entity.getActiveSessionId())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
            .updatedAt(MysqlTimeConvertor.toInstant(entity.getUpdatedAt()))
            .lastMessageAt(MysqlTimeConvertor.toInstant(entity.getLastMessageAt()))
            .build();
    }

    public AgentConversationEntity toEntity(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        AgentConversationEntity entity = new AgentConversationEntity();
        entity.setConversationId(conversation.getConversationId());
        entity.setTitle(conversation.getTitle());
        entity.setStatus(conversation.getStatus());
        entity.setActiveSessionId(conversation.getActiveSessionId());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(conversation.getCreatedAt())));
        entity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(conversation.getUpdatedAt())));
        entity.setLastMessageAt(MysqlTimeConvertor.toLocalDateTime(conversation.getLastMessageAt()));
        return entity;
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
