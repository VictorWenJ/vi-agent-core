package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateCacheDocument;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateMessageDocument;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStateRedisMapperModelContextFilterTest {

    @Test
    void toDocumentShouldExcludeToolCallMessage() {
        SessionStateRedisMapper mapper = new SessionStateRedisMapper();
        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .messages(List.of(
                UserMessage.create("msg-user", "turn-1", 1L, "现在几点"),
                ToolCallMessage.create("msg-tool-call", "turn-1", 2L, "call-1", "get_time", "{}"),
                ToolResultMessage.create("msg-tool-result", "turn-1", 3L, "call-1", "get_time", true, "2026-04-21T16:00:00+08:00", null, null, 1L)
            ))
            .updatedAt(Instant.now())
            .build();

        SessionStateCacheDocument document = mapper.toDocument(snapshot);

        assertEquals(2, document.getMessageDocuments().size());
        assertTrue(document.getMessageDocuments().stream()
            .noneMatch(message -> MessageType.TOOL_CALL.name().equals(message.getMessageType())));
    }

    @Test
    void toModelShouldExcludeToolCallMessage() {
        SessionStateRedisMapper mapper = new SessionStateRedisMapper();
        SessionStateCacheDocument document = new SessionStateCacheDocument(
            "sess-1",
            "conv-1",
            List.of(
                new SessionStateMessageDocument("msg-user", "turn-1", "USER", MessageType.USER_INPUT.name(), 1L, "现在几点", Instant.now()),
                new SessionStateMessageDocument("msg-tool-call", "turn-1", "TOOL", MessageType.TOOL_CALL.name(), 2L, "{}", Instant.now()),
                new SessionStateMessageDocument("msg-tool-result", "turn-1", "TOOL", MessageType.TOOL_RESULT.name(), 3L, "2026-04-21T16:00:00+08:00", Instant.now())
            ),
            null,
            null,
            null,
            Instant.now()
        );

        SessionStateSnapshot snapshot = mapper.toModel(document);

        assertEquals(2, snapshot.getMessages().size());
        assertTrue(snapshot.getMessages().stream().noneMatch(ToolCallMessage.class::isInstance));
        assertTrue(snapshot.getMessages().stream().anyMatch(ToolResultMessage.class::isInstance));
    }
}

