package com.vi.agent.core.infra.persistence.mysql.convertor;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentSessionEntity;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SessionMysqlConvertor {

    public Session toModel(AgentSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        return Session.builder()
            .sessionId(entity.getSessionId())
            .conversationId(entity.getConversationId())
            .parentSessionId(entity.getParentSessionId())
            .status(SessionStatus.valueOf(entity.getStatus()))
            .archiveReason(entity.getArchiveReason())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
            .updatedAt(MysqlTimeConvertor.toInstant(entity.getUpdatedAt()))
            .archivedAt(MysqlTimeConvertor.toInstant(entity.getArchivedAt()))
            .build();
    }

    public AgentSessionEntity toEntity(Session session) {
        if (session == null) {
            return null;
        }
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setSessionId(session.getSessionId());
        entity.setConversationId(session.getConversationId());
        entity.setParentSessionId(session.getParentSessionId());
        entity.setStatus(session.getStatus().name());
        entity.setArchiveReason(session.getArchiveReason());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(session.getCreatedAt())));
        entity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(session.getUpdatedAt())));
        entity.setArchivedAt(MysqlTimeConvertor.toLocalDateTime(session.getArchivedAt()));
        return entity;
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
