package com.vi.agent.core.infra.persistence.message.handler;

import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.ToolMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * ToolMessage handler.
 */
@Component
public class ToolMessageTypeHandler implements MessageTypeHandler<ToolMessage> {

    @Override
    public MessageRole role() {
        return MessageRole.TOOL;
    }

    @Override
    public List<MessageType> supportedTypes() {
        return List.of(MessageType.TOOL_RESULT);
    }

    @Override
    public ToolMessage assemble(MessageAggregateRows rows) {
        AgentMessageEntity entity = rows.getMessage();
        AgentToolExecutionEntity execution = rows.getToolExecution();

        return ToolMessage.restore(
            entity.getMessageId(),
            entity.getConversationId(),
            entity.getSessionId(),
            entity.getTurnId(),
            entity.getRunId(),
            entity.getSequenceNo(),
            entity.getStatus(),
            entity.getContentText(),
            entity.getToolCallRecordId(),
            entity.getToolCallId(),
            entity.getToolName(),
            execution == null ? null : execution.getStatus(),
            execution == null ? null : execution.getErrorCode(),
            execution == null ? null : execution.getErrorMessage(),
            execution == null ? null : execution.getDurationMs(),
            execution == null ? null : execution.getArgumentsJson(),
            MysqlTimeConvertor.toInstant(entity.getCreatedAt())
        );
    }

    @Override
    public MessageWritePlan decompose(ToolMessage message) {
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setMessageId(message.getMessageId());
        entity.setConversationId(message.getConversationId());
        entity.setSessionId(message.getSessionId());
        entity.setTurnId(message.getTurnId());
        entity.setRunId(message.getRunId());
        entity.setRole(message.getRole());
        entity.setMessageType(message.getMessageType());
        entity.setSequenceNo(message.getSequenceNo());
        entity.setStatus(message.getStatus());
        entity.setContentText(message.getContentText());
        entity.setToolCallRecordId(message.getToolCallRecordId());
        entity.setToolCallId(message.getToolCallId());
        entity.setToolName(message.getToolName());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));
        entity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));

        AgentToolExecutionEntity execution = new AgentToolExecutionEntity();
        execution.setToolExecutionId("tex-" + message.getMessageId());
        execution.setToolCallRecordId(message.getToolCallRecordId());
        execution.setToolCallId(message.getToolCallId());
        execution.setToolResultMessageId(message.getMessageId());
        execution.setConversationId(message.getConversationId());
        execution.setSessionId(message.getSessionId());
        execution.setTurnId(message.getTurnId());
        execution.setRunId(message.getRunId());
        execution.setToolName(message.getToolName());
        execution.setArgumentsJson(message.getArgumentsJson());
        execution.setOutputText(message.getContentText());
        execution.setOutputJson(message.getContentText());
        execution.setStatus(message.getExecutionStatus());
        execution.setErrorCode(message.getErrorCode());
        execution.setErrorMessage(message.getErrorMessage());
        execution.setDurationMs(message.getDurationMs());
        execution.setStartedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));
        execution.setCompletedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));
        execution.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));
        execution.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(message.getCreatedAt())));

        return MessageWritePlan.builder()
            .message(entity)
            .toolExecution(execution)
            .build();
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
