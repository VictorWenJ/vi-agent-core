package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.MessageMysqlConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageMapper;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MessageRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MysqlMessageRepository implements MessageRepository {

    @Resource
    private AgentMessageMapper mapper;

    @Resource
    private MessageMysqlConvertor convertor;

    @Override
    public void save(String conversationId, String sessionId, Message message) {
        mapper.insert(convertor.toEntity(conversationId, sessionId, message));
    }

    @Override
    public Message findByMessageId(String messageId) {
        AgentMessageEntity entity = mapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getMessageId, messageId)
                .last("limit 1")
        );
        return Optional.ofNullable(entity).map(convertor::toModel).orElse(null);
    }

    @Override
    public List<Message> findBySessionIdOrderBySequence(String sessionId, int limit) {
        List<AgentMessageEntity> entities = mapper.selectList(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getSessionId, sessionId)
                .orderByAsc(AgentMessageEntity::getSequenceNo)
                .last("limit " + limit)
        );
        return entities.stream().map(convertor::toModel).toList();
    }

    @Override
    public long nextSequenceNo(String sessionId) {
        AgentMessageEntity entity = mapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getSessionId, sessionId)
                .orderByDesc(AgentMessageEntity::getSequenceNo)
                .last("limit 1")
        );
        if (entity == null || entity.getSequenceNo() == null) {
            return 1L;
        }
        return entity.getSequenceNo() + 1;
    }
}
