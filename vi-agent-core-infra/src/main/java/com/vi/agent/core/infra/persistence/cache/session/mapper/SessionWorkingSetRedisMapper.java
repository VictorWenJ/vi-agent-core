package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.cache.session.document.AssistantToolCallSnapshot;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionWorkingSetMessageSnapshot;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionWorkingSetSnapshotDocument;
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
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Session working set Redis 映射器。
 */
@Component
public class SessionWorkingSetRedisMapper {

    public SessionWorkingSetSnapshotDocument toDocument(SessionWorkingSetSnapshot snapshot) {
        List<Message> messages = CollectionUtils.isEmpty(snapshot.getMessages()) ? List.of() : snapshot.getMessages();
        List<SessionWorkingSetMessageSnapshot> messageSnapshots = messages.stream().map(this::toMessageSnapshot).toList();

        return SessionWorkingSetSnapshotDocument.builder()
            .sessionId(snapshot.getSessionId())
            .conversationId(snapshot.getConversationId())
            .workingSetVersion(snapshot.getWorkingSetVersion())
            .maxCompletedTurns(snapshot.getMaxCompletedTurns())
            .summaryCoveredToSequenceNo(snapshot.getSummaryCoveredToSequenceNo())
            .rawMessageIdsJson(JsonUtils.toJson(CollectionUtils.isEmpty(snapshot.getRawMessageIds())
                ? List.of()
                : snapshot.getRawMessageIds()))
            .snapshotVersion(1)
            .messagesJson(JsonUtils.toJson(messageSnapshots))
            .updatedAtEpochMs(toEpochMillis(snapshot.getUpdatedAt()))
            .build();
    }

    public SessionWorkingSetSnapshot toModel(SessionWorkingSetSnapshotDocument document) {
        List<String> rawMessageIds = parseRawMessageIds(document.getRawMessageIdsJson());
        List<SessionWorkingSetMessageSnapshot> messageSnapshots = JsonUtils.jsonToBean(
            document.getMessagesJson(),
            new TypeReference<List<SessionWorkingSetMessageSnapshot>>() {
            }.getType()
        );

        List<Message> messages = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(messageSnapshots)) {
            for (SessionWorkingSetMessageSnapshot messageSnapshot : messageSnapshots) {
                messages.add(toMessage(messageSnapshot));
            }
        }

        return SessionWorkingSetSnapshot.builder()
            .sessionId(document.getSessionId())
            .conversationId(document.getConversationId())
            .workingSetVersion(document.getWorkingSetVersion())
            .maxCompletedTurns(document.getMaxCompletedTurns())
            .summaryCoveredToSequenceNo(document.getSummaryCoveredToSequenceNo())
            .rawMessageIds(rawMessageIds)
            .messages(messages.stream().sorted(Comparator.comparingLong(Message::getSequenceNo)).toList())
            .updatedAt(fromEpochMillis(document.getUpdatedAtEpochMs()))
            .build();
    }

    private SessionWorkingSetMessageSnapshot toMessageSnapshot(Message message) {
        SessionWorkingSetMessageSnapshot.SessionWorkingSetMessageSnapshotBuilder builder = SessionWorkingSetMessageSnapshot.builder()
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

    private Message toMessage(SessionWorkingSetMessageSnapshot snapshot) {
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

    private List<String> parseRawMessageIds(String rawMessageIdsJson) {
        if (StringUtils.isBlank(rawMessageIdsJson)) {
            return List.of();
        }
        try {
            List<String> rawMessageIds = JsonUtils.jsonToBean(
                rawMessageIdsJson,
                new TypeReference<List<String>>() {
                }.getType()
            );
            return CollectionUtils.isEmpty(rawMessageIds) ? List.of() : rawMessageIds;
        } catch (Exception ex) {
            return List.of();
        }
    }
}
