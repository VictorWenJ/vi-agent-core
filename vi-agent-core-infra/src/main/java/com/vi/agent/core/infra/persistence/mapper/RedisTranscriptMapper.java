package com.vi.agent.core.infra.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.entity.TranscriptEntity;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolExecutionMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transcript Redis 映射器。
 */
public class RedisTranscriptMapper {

    /**
     * 模型对象转持久化实体。
     *
     * @param transcript Transcript 模型
     * @return 持久化实体
     */
    public TranscriptEntity toEntity(ConversationTranscript transcript) {
        List<PersistedMessage> persistedMessages = new ArrayList<>();
        for (Message message : transcript.getMessages()) {
            persistedMessages.add(toPersistedMessage(message));
        }
        return TranscriptEntity.builder()
            .sessionId(transcript.getSessionId())
            .conversationId(transcript.getConversationId())
            .traceId(transcript.getTraceId())
            .runId(transcript.getRunId())
            .messagesJson(JsonUtils.toJson(persistedMessages))
            .toolCallsJson(JsonUtils.toJson(transcript.getToolCalls()))
            .toolResultsJson(JsonUtils.toJson(transcript.getToolResults()))
            .updatedAt(Instant.now())
            .build();
    }

    /**
     * 持久化实体转模型对象。
     *
     * @param entity 持久化实体
     * @return Transcript 模型
     */
    public ConversationTranscript toModel(TranscriptEntity entity) {
        List<PersistedMessage> persistedMessages = JsonUtils.jsonToBean(
            entity.getMessagesJson(),
            new TypeReference<List<PersistedMessage>>() {
            }.getType()
        );
        List<Message> messages = new ArrayList<>();
        if (persistedMessages != null) {
            for (PersistedMessage persistedMessage : persistedMessages) {
                messages.add(toMessage(persistedMessage));
            }
        }

        List<ToolCall> toolCalls = JsonUtils.jsonToBean(
            entity.getToolCallsJson(),
            new TypeReference<List<ToolCall>>() {
            }.getType()
        );

        List<ToolResult> toolResults = JsonUtils.jsonToBean(
            entity.getToolResultsJson(),
            new TypeReference<List<ToolResult>>() {
            }.getType()
        );
        return ConversationTranscript.restore(
            entity.getSessionId(),
            entity.getConversationId(),
            entity.getTraceId(),
            entity.getRunId(),
            messages,
            toolCalls == null ? Collections.emptyList() : toolCalls,
            toolResults == null ? Collections.emptyList() : toolResults,
            entity.getUpdatedAt()
        );
    }

    private PersistedMessage toPersistedMessage(Message message) {
        PersistedMessage.PersistedMessageBuilder builder = PersistedMessage.builder()
            .messageId(message.getMessageId())
            .turnId(message.getTurnId())
            .role(message.getRole())
            .content(message.getContent())
            .createdAt(message.getCreatedAt());

        if (message instanceof AssistantMessage assistantMessage) {
            builder.toolCalls(assistantMessage.getToolCalls());
        }
        if (message instanceof ToolExecutionMessage toolExecutionMessage) {
            builder.toolCallId(toolExecutionMessage.getToolCallId());
            builder.toolName(toolExecutionMessage.getToolName());
            builder.toolOutput(toolExecutionMessage.getToolOutput());
        }
        return builder.build();
    }

    private Message toMessage(PersistedMessage persistedMessage) {
        if (persistedMessage == null) {
            return UserMessage.create(null, null, "");
        }
        Instant createdAt = persistedMessage.getCreatedAt() == null ? Instant.now() : persistedMessage.getCreatedAt();
        if ("assistant".equalsIgnoreCase(persistedMessage.getRole())) {
            List<ToolCall> toolCalls = persistedMessage.getToolCalls() == null
                ? Collections.emptyList()
                : persistedMessage.getToolCalls();
            return AssistantMessage.restore(
                persistedMessage.getMessageId(),
                persistedMessage.getTurnId(),
                persistedMessage.getContent(),
                toolCalls,
                createdAt
            );
        }
        if ("tool".equalsIgnoreCase(persistedMessage.getRole())) {
            return ToolExecutionMessage.restore(
                persistedMessage.getMessageId(),
                persistedMessage.getTurnId(),
                persistedMessage.getToolCallId(),
                persistedMessage.getToolName(),
                persistedMessage.getToolOutput() == null ? persistedMessage.getContent() : persistedMessage.getToolOutput(),
                createdAt
            );
        }
        return UserMessage.restore(
            persistedMessage.getMessageId(),
            persistedMessage.getTurnId(),
            persistedMessage.getContent(),
            createdAt
        );
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class PersistedMessage {

        /** 消息 ID。 */
        private String messageId;

        /** 消息角色。 */
        private String role;

        /** 当前轮次 ID。 */
        private String turnId;

        /** 消息内容。 */
        private String content;

        /** 消息时间。 */
        private Instant createdAt;

        /** 工具调用 ID。 */
        private String toolCallId;

        /** 工具名称。 */
        private String toolName;

        /** 工具输出。 */
        private String toolOutput;

        /** assistant 携带的工具调用。 */
        private List<ToolCall> toolCalls;
    }
}
