package com.vi.agent.core.infra.persistence.mysql.convertor;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolResultEntity;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ToolExecutionMysqlConvertor {

    public AgentToolCallEntity toEntity(ToolCallRecord record) {
        AgentToolCallEntity entity = new AgentToolCallEntity();
        entity.setToolCallId(record.getToolCallId());
        entity.setConversationId(record.getConversationId());
        entity.setSessionId(record.getSessionId());
        entity.setTurnId(record.getTurnId());
        entity.setMessageId(record.getMessageId());
        entity.setToolName(record.getToolName());
        entity.setArgumentsJson(record.getArgumentsJson());
        entity.setSequenceNo(record.getSequenceNo());
        entity.setStatus(record.getStatus());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(record.getCreatedAt())));
        return entity;
    }

    public AgentToolResultEntity toEntity(ToolResultRecord record) {
        AgentToolResultEntity entity = new AgentToolResultEntity();
        entity.setToolCallId(record.getToolCallId());
        entity.setConversationId(record.getConversationId());
        entity.setSessionId(record.getSessionId());
        entity.setTurnId(record.getTurnId());
        entity.setMessageId(record.getMessageId());
        entity.setToolName(record.getToolName());
        entity.setSuccess(record.isSuccess());
        entity.setOutputJson(record.getOutputJson());
        entity.setErrorCode(record.getErrorCode());
        entity.setErrorMessage(record.getErrorMessage());
        entity.setDurationMs(record.getDurationMs());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(record.getCreatedAt())));
        return entity;
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
