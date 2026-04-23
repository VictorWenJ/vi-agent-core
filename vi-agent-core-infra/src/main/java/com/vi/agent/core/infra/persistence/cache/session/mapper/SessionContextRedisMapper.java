package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.cache.session.document.AssistantToolCallSnapshot;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionContextMessageSnapshot;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionContextSnapshotDocument;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageStatus;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.SummaryMessage;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Session 上下文 Redis 快照映射器。
 */
@Component
public class SessionContextRedisMapper {

    public SessionContextSnapshotDocument toDocument(SessionStateSnapshot snapshot) {
        List<Message> messages = CollectionUtils.isEmpty(snapshot.getMessages()) ? List.of() : snapshot.getMessages();
        List<SessionContextMessageSnapshot> messageSnapshots = messages.stream().map(this::toMessageSnapshot).toList();

        Long fromSequenceNo = messages.stream().map(Message::getSequenceNo).min(Long::compareTo).orElse(0L);
        Long toSequenceNo = messages.stream().map(Message::getSequenceNo).max(Long::compareTo).orElse(0L);

        return SessionContextSnapshotDocument.builder()
            .sessionId(snapshot.getSessionId())
            .conversationId(snapshot.getConversationId())
            .fromSequenceNo(fromSequenceNo)
            .toSequenceNo(toSequenceNo)
            .messageCount(messageSnapshots.size())
            .snapshotVersion(1)
            .messagesJson(JsonUtils.toJson(messageSnapshots))
            .updatedAtEpochMs(toEpochMillis(snapshot.getUpdatedAt()))
            .build();
    }

    public SessionStateSnapshot toModel(SessionContextSnapshotDocument document) {
        List<SessionContextMessageSnapshot> messageSnapshots = JsonUtils.jsonToBean(
            document.getMessagesJson(),
            new TypeReference<List<SessionContextMessageSnapshot>>() {
            }.getType()
        );

        List<Message> messages = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(messageSnapshots)) {
            for (SessionContextMessageSnapshot messageSnapshot : messageSnapshots) {
                messages.add(toMessage(messageSnapshot));
            }
        }

        return SessionStateSnapshot.builder()
            .sessionId(document.getSessionId())
            .conversationId(document.getConversationId())
            .messages(messages.stream().sorted(Comparator.comparingLong(Message::getSequenceNo)).toList())
            .updatedAt(fromEpochMillis(document.getUpdatedAtEpochMs()))
            .build();
    }

    private SessionContextMessageSnapshot toMessageSnapshot(Message message) {
        SessionContextMessageSnapshot.SessionContextMessageSnapshotBuilder builder = SessionContextMessageSnapshot.builder()
            .messageId(message.getMessageId())
            .conversationId(message.getConversationId())
            .sessionId(message.getSessionId())
            .turnId(message.getTurnId())
            .runId(message.getRunId())
            .role(message.getRole())
            .messageType(message.getMessageType())
            .sequenceNo(message.getSequenceNo())
            .status(message.getStatus())
            .contentText(message.getContentText())
            .createdAtEpochMs(toEpochMillis(message.getCreatedAt()));

        if (message instanceof AssistantMessage assistantMessage) {
            builder.finishReason(assistantMessage.getFinishReason() == null ? null : assistantMessage.getFinishReason().name());
            if (assistantMessage.getUsage() != null) {
                builder.usageInputTokens(assistantMessage.getUsage().getInputTokens());
                builder.usageOutputTokens(assistantMessage.getUsage().getOutputTokens());
                builder.usageTotalTokens(assistantMessage.getUsage().getTotalTokens());
                builder.usageProvider(assistantMessage.getUsage().getProvider());
                builder.usageModel(assistantMessage.getUsage().getModel());
            }
            List<AssistantToolCallSnapshot> toolCallSnapshots = CollectionUtils.isEmpty(assistantMessage.getToolCalls())
                ? List.of()
                : assistantMessage.getToolCalls().stream().filter(Objects::nonNull).map(toolCall -> AssistantToolCallSnapshot.builder()
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
                    .status(toolCall.getStatus() == null ? null : toolCall.getStatus().name())
                    .createdAtEpochMs(toEpochMillis(toolCall.getCreatedAt()))
                    .build()).toList();
            builder.toolCalls(toolCallSnapshots);
        }

        if (message instanceof ToolMessage toolMessage) {
            builder.toolCallRecordId(toolMessage.getToolCallRecordId())
                .toolCallId(toolMessage.getToolCallId())
                .toolName(toolMessage.getToolName())
                .executionStatus(toolMessage.getExecutionStatus() == null ? null : toolMessage.getExecutionStatus().name())
                .errorCode(toolMessage.getErrorCode())
                .errorMessage(toolMessage.getErrorMessage())
                .durationMs(toolMessage.getDurationMs())
                .argumentsJson(toolMessage.getArgumentsJson());
        }

        return builder.build();
    }

    private Message toMessage(SessionContextMessageSnapshot snapshot) {
        MessageRole role = snapshot.getRole() ;
        MessageType messageType = snapshot.getMessageType();
        MessageStatus status = snapshot.getStatus();
        Instant createdAt = fromEpochMillis(snapshot.getCreatedAtEpochMs());

        if (role == MessageRole.USER && messageType == MessageType.USER_INPUT) {
            return UserMessage.restore(
                snapshot.getMessageId(),
                snapshot.getConversationId(),
                snapshot.getSessionId(),
                snapshot.getTurnId(),
                snapshot.getRunId(),
                safeLong(snapshot.getSequenceNo()),
                snapshot.getStatus(),
                snapshot.getContentText(),
                createdAt
            );
        }

        if (role == MessageRole.ASSISTANT && messageType == MessageType.ASSISTANT_OUTPUT) {
            List<AssistantToolCall> toolCalls = CollectionUtils.isEmpty(snapshot.getToolCalls())
                ? List.of()
                : snapshot.getToolCalls().stream().map(toolCallSnapshot -> AssistantToolCall.builder()
                    .toolCallRecordId(toolCallSnapshot.getToolCallRecordId())
                    .toolCallId(toolCallSnapshot.getToolCallId())
                    .assistantMessageId(toolCallSnapshot.getAssistantMessageId())
                    .conversationId(toolCallSnapshot.getConversationId())
                    .sessionId(toolCallSnapshot.getSessionId())
                    .turnId(toolCallSnapshot.getTurnId())
                    .runId(toolCallSnapshot.getRunId())
                    .toolName(toolCallSnapshot.getToolName())
                    .argumentsJson(toolCallSnapshot.getArgumentsJson())
                    .callIndex(toolCallSnapshot.getCallIndex())
                    .status(toolCallSnapshot.getStatus() == null ? null : ToolCallStatus.valueOf(toolCallSnapshot.getStatus()))
                    .createdAt(fromEpochMillis(toolCallSnapshot.getCreatedAtEpochMs()))
                    .build()).toList();
            UsageInfo usage = null;
            if (snapshot.getUsageInputTokens() != null || snapshot.getUsageOutputTokens() != null || snapshot.getUsageTotalTokens() != null) {
                usage = UsageInfo.builder()
                    .inputTokens(snapshot.getUsageInputTokens())
                    .outputTokens(snapshot.getUsageOutputTokens())
                    .totalTokens(snapshot.getUsageTotalTokens())
                    .provider(snapshot.getUsageProvider())
                    .model(snapshot.getUsageModel())
                    .build();
            }
            return AssistantMessage.restore(
                snapshot.getMessageId(),
                snapshot.getConversationId(),
                snapshot.getSessionId(),
                snapshot.getTurnId(),
                snapshot.getRunId(),
                safeLong(snapshot.getSequenceNo()),
                status,
                snapshot.getContentText(),
                toolCalls,
                snapshot.getFinishReason() == null ? null : com.vi.agent.core.model.llm.FinishReason.valueOf(snapshot.getFinishReason()),
                usage,
                createdAt
            );
        }

        if (role == MessageRole.TOOL && messageType == MessageType.TOOL_RESULT) {
            return ToolMessage.restore(
                snapshot.getMessageId(),
                snapshot.getConversationId(),
                snapshot.getSessionId(),
                snapshot.getTurnId(),
                snapshot.getRunId(),
                safeLong(snapshot.getSequenceNo()),
                status,
                snapshot.getContentText(),
                snapshot.getToolCallRecordId(),
                snapshot.getToolCallId(),
                snapshot.getToolName(),
                snapshot.getExecutionStatus() == null ? null : ToolExecutionStatus.valueOf(snapshot.getExecutionStatus()),
                snapshot.getErrorCode(),
                snapshot.getErrorMessage(),
                snapshot.getDurationMs(),
                snapshot.getArgumentsJson(),
                createdAt
            );
        }

        if (role == MessageRole.SYSTEM && messageType == MessageType.SYSTEM_PROMPT) {
            return SystemMessage.restore(
                snapshot.getMessageId(),
                snapshot.getConversationId(),
                snapshot.getSessionId(),
                snapshot.getTurnId(),
                snapshot.getRunId(),
                safeLong(snapshot.getSequenceNo()),
                status,
                snapshot.getContentText(),
                createdAt
            );
        }

        if (role == MessageRole.SUMMARY && messageType == MessageType.SUMMARY_CONTEXT) {
            return SummaryMessage.restore(
                snapshot.getMessageId(),
                snapshot.getConversationId(),
                snapshot.getSessionId(),
                snapshot.getTurnId(),
                snapshot.getRunId(),
                safeLong(snapshot.getSequenceNo()),
                status,
                snapshot.getContentText(),
                createdAt
            );
        }

        throw new IllegalStateException("Unsupported snapshot role/messageType: " + role + "/" + messageType);
    }

    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private Instant fromEpochMillis(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
