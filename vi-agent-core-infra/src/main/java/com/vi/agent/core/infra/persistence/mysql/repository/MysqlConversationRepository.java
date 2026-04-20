package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.ConversationMysqlConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentConversationEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentConversationMapper;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.port.ConversationRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MysqlConversationRepository implements ConversationRepository {

    @Resource
    private AgentConversationMapper mapper;

    @Resource
    private ConversationMysqlConvertor convertor;

    @Override
    public Optional<Conversation> findByConversationId(String conversationId) {
        AgentConversationEntity entity = mapper.selectOne(
            Wrappers.lambdaQuery(AgentConversationEntity.class)
                .eq(AgentConversationEntity::getConversationId, conversationId)
                .last("limit 1")
        );
        return Optional.ofNullable(convertor.toModel(entity));
    }

    @Override
    public void save(Conversation conversation) {
        AgentConversationEntity entity = convertor.toEntity(conversation);
        mapper.insert(entity);
    }

    @Override
    public void update(Conversation conversation) {
        AgentConversationEntity entity = convertor.toEntity(conversation);
        mapper.update(
            entity,
            Wrappers.lambdaUpdate(AgentConversationEntity.class)
                .eq(AgentConversationEntity::getConversationId, conversation.getConversationId())
        );
    }
}
