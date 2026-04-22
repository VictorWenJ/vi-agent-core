package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.TurnMysqlConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentTurnEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentTurnMapper;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class MysqlTurnRepository implements TurnRepository {

    @Resource
    private AgentTurnMapper mapper;

    @Resource
    private TurnMysqlConvertor convertor;

    @Override
    public Turn findByRequestId(String requestId) {
        AgentTurnEntity entity = mapper.selectOne(
            Wrappers.lambdaQuery(AgentTurnEntity.class)
                .eq(AgentTurnEntity::getRequestId, requestId)
                .last("limit 1")
        );
        return convertor.toModel(entity);
    }

    @Override
    public Turn findByTurnId(String turnId) {
        AgentTurnEntity entity = mapper.selectOne(
            Wrappers.lambdaQuery(AgentTurnEntity.class)
                .eq(AgentTurnEntity::getTurnId, turnId)
                .last("limit 1")
        );
        return convertor.toModel(entity);
    }

    @Override
    public boolean existsRunningBySessionId(String sessionId) {
        Long count = mapper.selectCount(
            Wrappers.lambdaQuery(AgentTurnEntity.class)
                .eq(AgentTurnEntity::getSessionId, sessionId)
                .eq(AgentTurnEntity::getStatus, TurnStatus.RUNNING)
        );
        return count != null && count > 0;
    }

    @Override
    public void save(Turn turn) {
        mapper.insert(convertor.toEntity(turn));
    }

    @Override
    public void update(Turn turn) {
        mapper.update(
            convertor.toEntity(turn),
            Wrappers.lambdaUpdate(AgentTurnEntity.class)
                .eq(AgentTurnEntity::getTurnId, turn.getTurnId())
        );
    }
}
