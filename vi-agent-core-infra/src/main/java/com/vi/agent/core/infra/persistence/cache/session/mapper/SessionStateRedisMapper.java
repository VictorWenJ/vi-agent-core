package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateCacheDocument;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateMessageDocument;
import com.vi.agent.core.model.message.*;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Mapper between session snapshot and redis document.
 */
@Component
public class SessionStateRedisMapper {

    public SessionStateCacheDocument toDocument(SessionStateSnapshot snapshot) {
        List<SessionStateMessageDocument> messages = snapshot.getMessages().stream()
            .map(this::toMessageDocument)
            .toList();
        return new SessionStateCacheDocument(
            snapshot.getSessionId(),
            snapshot.getConversationId(),
            messages,
            null,
            null,
            null,
            snapshot.getUpdatedAt()
        );
    }

    public SessionStateSnapshot toModel(SessionStateCacheDocument document) {
        List<Message> messages = document.getMessages() == null
            ? List.of()
            : document.getMessages().stream().map(this::toMessage).toList();
        return SessionStateSnapshot.builder()
            .sessionId(document.getSessionId())
            .conversationId(document.getConversationId())
            .messages(messages)
            .updatedAt(document.getUpdatedAt())
            .build();
    }

    private SessionStateMessageDocument toMessageDocument(Message message) {
        return new SessionStateMessageDocument(
            message.getMessageId(),
            message.getTurnId(),
            message.getRole().name(),
            message.getMessageType().name(),
            message.getSequenceNo(),
            message.getContent(),
            message.getCreatedAt()
        );
    }

    private Message toMessage(SessionStateMessageDocument document) {
        MessageType messageType = MessageType.valueOf(document.getMessageType());
        Instant createdAt = document.getCreatedAt();
        return switch (messageType) {
            case USER_INPUT -> UserMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getContent(), createdAt);
            case ASSISTANT_OUTPUT -> AssistantMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getContent(), List.of(), createdAt);
            case TOOL_CALL -> ToolCallMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getMessageId(), "tool", document.getContent(), createdAt);
            case TOOL_RESULT -> ToolResultMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getMessageId(), "tool", true, document.getContent(), null, null, null, createdAt);
            case SYSTEM_MESSAGE, SUMMARY_MESSAGE -> AssistantMessage.restore(document.getMessageId(), document.getTurnId(), document.getSequenceNo(), document.getContent(), List.of(), createdAt);
        };
    }
}
