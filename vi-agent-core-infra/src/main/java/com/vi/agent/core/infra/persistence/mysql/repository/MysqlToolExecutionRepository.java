package com.vi.agent.core.infra.persistence.mysql.repository;

import com.vi.agent.core.infra.persistence.mysql.convertor.ToolExecutionMysqlConvertor;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolCallMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolResultMapper;
import com.vi.agent.core.model.port.ToolExecutionRepository;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

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
}
