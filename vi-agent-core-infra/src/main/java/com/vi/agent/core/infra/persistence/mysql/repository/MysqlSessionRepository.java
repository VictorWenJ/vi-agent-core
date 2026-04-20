package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.SessionMysqlConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentSessionEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentSessionMapper;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MysqlSessionRepository implements SessionRepository {

    @Resource
    private AgentSessionMapper mapper;

    @Resource
    private SessionMysqlConvertor convertor;

    @Override
    public Optional<Session> findBySessionId(String sessionId) {
        AgentSessionEntity entity = mapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionEntity.class)
                .eq(AgentSessionEntity::getSessionId, sessionId)
                .last("limit 1")
        );
        return Optional.ofNullable(convertor.toModel(entity));
    }

    @Override
    public Optional<Session> findActiveByConversationId(String conversationId) {
        AgentSessionEntity entity = mapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionEntity.class)
                .eq(AgentSessionEntity::getConversationId, conversationId)
                .eq(AgentSessionEntity::getStatus, SessionStatus.ACTIVE.name())
                .last("limit 1")
        );
        return Optional.ofNullable(convertor.toModel(entity));
    }

    @Override
    public void save(Session session) {
        mapper.insert(convertor.toEntity(session));
    }

    @Override
    public void update(Session session) {
        mapper.update(
            convertor.toEntity(session),
            Wrappers.lambdaUpdate(AgentSessionEntity.class)
                .eq(AgentSessionEntity::getSessionId, session.getSessionId())
        );
    }
}
