package com.vi.agent.core.infra.persistence.message.handler;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AssistantMessage handler.
 */
@Component
public class AssistantMessageTypeHandler implements MessageTypeHandler<AssistantMessage> {

    @Override
    public MessageRole role() {
        return MessageRole.ASSISTANT;
    }

    @Override
    public List<MessageType> supportedTypes() {
        return List.of(MessageType.ASSISTANT_OUTPUT);
    }

    @Override
    public AssistantMessage assemble(MessageAggregateRows rows) {
        AgentMessageEntity entity = rows.getMessage();
        List<AssistantToolCall> toolCalls = CollectionUtils.isEmpty(rows.getToolCalls())
            ? List.of()
            : rows.getToolCalls().stream()
                .sorted(Comparator.comparing(AgentMessageToolCallEntity::getCallIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(toolCall -> AssistantToolCall.builder()
                    .toolCallRecordId(toolCall.getToolCallRecordId())
                    .toolCallId(toolCall.getToolCallId())
                    .assistantMessageId(toolCall.getAssistantMessageId())
                    .conversationId(toolCall.getConversationId())
                    .sessionId(toolCall.getSessionId())
                    .turnId(toolCall.getTurnId())
                    .runId(toolCall.getRunId())
                    .toolName(toolCall.getToolName())
                    .argumentsJson(toolCall.getArgumentsJson())
                    .callIndex(toolCall.getCallIndex())
                    .status(toolCall.getStatus())
                    .createdAt(MysqlTimeConvertor.toInstant(toolCall.getCreatedAt()))
                    .build())
                .toList();

        UsageInfo usage = JsonUtils.jsonToBean(entity.getMetadataJson(), UsageInfo.class);

        return AssistantMessage.restore(
            entity.getMessageId(),
            entity.getConversationId(),
            entity.getSessionId(),
            entity.getTurnId(),
            entity.getRunId(),
            entity.getSequenceNo(),
            entity.getStatus(),
            entity.getContentText(),
            toolCalls,
            entity.getFinishReason(),
            usage,
            MysqlTimeConvertor.toInstant(entity.getCreatedAt())
        );
    }

    @Override
    public MessageWritePlan decompose(AssistantMessage message) {
        Instant createdAt = defaultNow(message.getCreatedAt());

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
        entity.setFinishReason(message.getFinishReason());
        entity.setMetadataJson(JsonUtils.toJson(message.getUsage()));
        entity.setProvider(message.getUsage() == null ? null : message.getUsage().getProvider());
        entity.setModel(message.getUsage() == null ? null : message.getUsage().getModel());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(createdAt));
        entity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(createdAt));

        List<AgentMessageToolCallEntity> toolCallEntities = new ArrayList<>();
        if (!CollectionUtils.isEmpty(message.getToolCalls())) {
            for (AssistantToolCall toolCall : message.getToolCalls()) {
                Instant toolCallCreatedAt = defaultNow(toolCall.getCreatedAt());
                AgentMessageToolCallEntity toolCallEntity = new AgentMessageToolCallEntity();
                toolCallEntity.setToolCallRecordId(toolCall.getToolCallRecordId());
                toolCallEntity.setToolCallId(toolCall.getToolCallId());
                toolCallEntity.setAssistantMessageId(message.getMessageId());
                toolCallEntity.setConversationId(message.getConversationId());
                toolCallEntity.setSessionId(message.getSessionId());
                toolCallEntity.setTurnId(message.getTurnId());
                toolCallEntity.setRunId(message.getRunId());
                toolCallEntity.setToolName(toolCall.getToolName());
                toolCallEntity.setArgumentsJson(toolCall.getArgumentsJson());
                toolCallEntity.setCallIndex(toolCall.getCallIndex());
                toolCallEntity.setStatus(toolCall.getStatus());
                toolCallEntity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(toolCallCreatedAt));
                toolCallEntity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(toolCallCreatedAt));
                toolCallEntities.add(toolCallEntity);
            }
        }

        return MessageWritePlan.builder()
            .message(entity)
            .toolCalls(toolCallEntities)
            .build();
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
