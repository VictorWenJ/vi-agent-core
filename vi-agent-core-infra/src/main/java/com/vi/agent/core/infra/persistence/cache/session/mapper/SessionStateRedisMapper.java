package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateCacheDocument;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateMessageDocument;
import com.vi.agent.core.model.message.*;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mapper between session snapshot and redis document.
 */
@Component
public class SessionStateRedisMapper {

    public SessionStateCacheDocument toDocument(SessionStateSnapshot snapshot) {
        List<SessionStateMessageDocument> documents = Optional.ofNullable(snapshot.getMessages())
            .map(messages ->
                messages.stream()
                    .filter(document -> Objects.nonNull(document) && MessageType.TOOL_CALL != document.getMessageType())
                    .map(message -> SessionStateMessageDocument.builder()
                        .messageId(message.getMessageId())
                        .turnId(message.getTurnId())
                        .role(message.getRole().name())
                        .messageType(message.getMessageType().name())
                        .sequenceNo(message.getSequenceNo())
                        .content(message.getContent())
                        .createdAt(message.getCreatedAt())
                        .build())
                    .toList())
            .orElse(List.of());

        return SessionStateCacheDocument.builder()
            .sessionId(snapshot.getSessionId())
            .conversationId(snapshot.getConversationId())
            .messageDocuments(documents)
            .transcriptCache(null)
            .recentWindowCache(null)
            .summaryCheckpoint(null)
            .updatedAt(snapshot.getUpdatedAt())
            .build();
    }

    public SessionStateSnapshot toModel(SessionStateCacheDocument cacheDocument) {
        List<Message> messages = Optional.ofNullable(cacheDocument.getMessageDocuments())
            .map(documents ->
                documents.stream()
                    .filter(messageDocument -> Objects.nonNull(messageDocument) && !MessageType.TOOL_CALL.name().equals(messageDocument.getMessageType()))
                    .map(this::toMessage)
                    .filter(Objects::nonNull)
                    .toList())
            .orElse(List.of());

        return SessionStateSnapshot.builder()
            .sessionId(cacheDocument.getSessionId())
            .conversationId(cacheDocument.getConversationId())
            .messages(messages)
            .updatedAt(cacheDocument.getUpdatedAt())
            .build();
    }

    private Message toMessage(SessionStateMessageDocument document) {
        MessageType messageType = MessageType.valueOf(document.getMessageType());
        Instant createdAt = document.getCreatedAt();
        return switch (messageType) {
            case USER_INPUT ->
                UserMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getContent(), createdAt);
            case ASSISTANT_OUTPUT ->
                AssistantMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getContent(), List.of(), createdAt);
            case TOOL_CALL -> null;
            case TOOL_RESULT -> ToolResultMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getMessageId(), "tool", true, document.getContent(), null, null, null, createdAt);
            case SYSTEM_MESSAGE, SUMMARY_MESSAGE ->
                AssistantMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getContent(), List.of(), createdAt);
        };
    }
}
