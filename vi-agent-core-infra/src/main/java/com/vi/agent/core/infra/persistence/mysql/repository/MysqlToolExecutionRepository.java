package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.ToolExecutionMysqlConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolResultEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolCallMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolResultMapper;
import com.vi.agent.core.model.port.ToolExecutionRepository;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MysqlToolExecutionRepository implements ToolExecutionRepository {

    @Resource
    private AgentToolCallMapper toolCallMapper;

    @Resource
    private AgentToolResultMapper toolResultMapper;

    @Resource
    private ToolExecutionMysqlConvertor convertor;

    @Override
    public void saveToolCall(ToolCallRecord toolCallRecord) {
        toolCallMapper.insert(convertor.toEntity(toolCallRecord));
    }

    @Override
    public void saveToolResult(ToolResultRecord toolResultRecord) {
        toolResultMapper.insert(convertor.toEntity(toolResultRecord));
    }

    @Override
    public ToolCallRecord findToolCallByMessageId(String messageId) {
        AgentToolCallEntity entity = toolCallMapper.selectOne(
            Wrappers.lambdaQuery(AgentToolCallEntity.class)
                .eq(AgentToolCallEntity::getMessageId, messageId)
                .last("limit 1")
        );
        return Optional.ofNullable(entity).map(convertor::toModel).orElse(null);
    }

    @Override
    public ToolResultRecord findToolResultByMessageId(String messageId) {
        AgentToolResultEntity entity = toolResultMapper.selectOne(
            Wrappers.lambdaQuery(AgentToolResultEntity.class)
                .eq(AgentToolResultEntity::getMessageId, messageId)
                .last("limit 1")
        );
        return Optional.ofNullable(entity).map(convertor::toModel).orElse(null);
    }

    @Override
    public List<ToolCallRecord> findToolCallsByTurnId(String turnId) {
        return toolCallMapper.selectList(
                Wrappers.lambdaQuery(AgentToolCallEntity.class)
                    .eq(AgentToolCallEntity::getTurnId, turnId)
                    .orderByAsc(AgentToolCallEntity::getSequenceNo)
            ).stream()
            .map(convertor::toModel)
            .toList();
    }
}
