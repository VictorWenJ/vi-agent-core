package com.vi.agent.core.infra.persistence.mysql.convertor;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentTurnEntity;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TurnMysqlConvertor {

    public Turn toModel(AgentTurnEntity entity) {
        if (entity == null) {
            return null;
        }
        UsageInfo usage = null;
        if (entity.getTotalTokens() != null || entity.getInputTokens() != null || entity.getOutputTokens() != null) {
            usage = UsageInfo.builder()
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .totalTokens(entity.getTotalTokens())
                .provider(entity.getProvider())
                .model(entity.getModel())
                .build();
        }
        return Turn.builder()
            .turnId(entity.getTurnId())
            .conversationId(entity.getConversationId())
            .sessionId(entity.getSessionId())
            .requestId(entity.getRequestId())
            .runId(entity.getRunId())
            .status(TurnStatus.valueOf(entity.getStatus()))
            .userMessageId(entity.getUserMessageId())
            .assistantMessageId(entity.getAssistantMessageId())
            .finishReason(entity.getFinishReason() == null ? null : FinishReason.valueOf(entity.getFinishReason()))
            .usage(usage)
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
            .completedAt(MysqlTimeConvertor.toInstant(entity.getCompletedAt()))
            .build();
    }

    public AgentTurnEntity toEntity(Turn turn) {
        if (turn == null) {
            return null;
        }
        AgentTurnEntity entity = new AgentTurnEntity();
        entity.setTurnId(turn.getTurnId());
        entity.setConversationId(turn.getConversationId());
        entity.setSessionId(turn.getSessionId());
        entity.setRequestId(turn.getRequestId());
        entity.setRunId(turn.getRunId());
        entity.setStatus(turn.getStatus().name());
        entity.setUserMessageId(turn.getUserMessageId());
        entity.setAssistantMessageId(turn.getAssistantMessageId());
        entity.setFinishReason(turn.getFinishReason() == null ? null : turn.getFinishReason().name());
        if (turn.getUsage() != null) {
            entity.setInputTokens(turn.getUsage().getInputTokens());
            entity.setOutputTokens(turn.getUsage().getOutputTokens());
            entity.setTotalTokens(turn.getUsage().getTotalTokens());
            entity.setProvider(turn.getUsage().getProvider());
            entity.setModel(turn.getUsage().getModel());
        }
        entity.setErrorCode(turn.getErrorCode());
        entity.setErrorMessage(turn.getErrorMessage());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(turn.getCreatedAt())));
        entity.setCompletedAt(MysqlTimeConvertor.toLocalDateTime(turn.getCompletedAt()));
        return entity;
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}
