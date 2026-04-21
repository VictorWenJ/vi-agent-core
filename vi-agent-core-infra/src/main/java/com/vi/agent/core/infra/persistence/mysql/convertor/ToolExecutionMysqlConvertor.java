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

    public ToolCallRecord toModel(AgentToolCallEntity entity) {
        if (entity == null) {
            return null;
        }
        return ToolCallRecord.builder()
            .toolCallId(entity.getToolCallId())
            .conversationId(entity.getConversationId())
            .sessionId(entity.getSessionId())
            .turnId(entity.getTurnId())
            .messageId(entity.getMessageId())
            .toolName(entity.getToolName())
            .argumentsJson(entity.getArgumentsJson())
            .sequenceNo(entity.getSequenceNo() == null ? 0 : entity.getSequenceNo())
            .status(entity.getStatus())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
            .build();
    }

    public ToolResultRecord toModel(AgentToolResultEntity entity) {
        if (entity == null) {
            return null;
        }
        return ToolResultRecord.builder()
            .toolCallId(entity.getToolCallId())
            .conversationId(entity.getConversationId())
            .sessionId(entity.getSessionId())
            .turnId(entity.getTurnId())
            .messageId(entity.getMessageId())
            .toolName(entity.getToolName())
            .success(Boolean.TRUE.equals(entity.getSuccess()))
            .outputJson(entity.getOutputJson())
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())
            .durationMs(entity.getDurationMs())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
            .build();
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
