package com.vi.agent.core.runtime.state;

import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStateLoaderCompletedTurnFilterTest {

    @Test
    void loadShouldOnlyKeepMessagesFromCompletedTurns() throws Exception {
        SessionStateLoader loader = new SessionStateLoader();
        CapturingSessionStateRepository sessionStateRepository = new CapturingSessionStateRepository();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();

        setField(loader, "sessionStateRepository", sessionStateRepository);
        setField(loader, "messageRepository", messageRepository);
        setField(loader, "turnRepository", turnRepository);
        setField(loader, "maxWindow", 200);
        setField(loader, "modelContextMessageFilter", new com.vi.agent.core.runtime.context.ModelContextMessageFilter());

        Message completedTurnMessage = UserMessage.restore("msg-1", "turn-completed", 1L, "ok", Instant.now());
        Message failedTurnMessage = UserMessage.restore("msg-2", "turn-failed", 2L, "bad", Instant.now());
        messageRepository.messages = List.of(completedTurnMessage, failedTurnMessage);

        turnRepository.completedTurnIds.add("turn-completed");
        turnRepository.failedTurnIds.add("turn-failed");

        List<Message> loaded = loader.load("conv-1", "sess-1");

        assertEquals(1, loaded.size());
        assertEquals("msg-1", loaded.get(0).getMessageId());
        assertEquals(1, sessionStateRepository.savedSnapshot.getMessages().size());
        assertEquals("msg-1", sessionStateRepository.savedSnapshot.getMessages().get(0).getMessageId());
    }

    @Test
    void loadShouldExcludeToolCallMessageFromCachedSnapshot() throws Exception {
        SessionStateLoader loader = new SessionStateLoader();
        CapturingSessionStateRepository sessionStateRepository = new CapturingSessionStateRepository();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();

        setField(loader, "sessionStateRepository", sessionStateRepository);
        setField(loader, "messageRepository", messageRepository);
        setField(loader, "turnRepository", turnRepository);
        setField(loader, "maxWindow", 200);
        setField(loader, "modelContextMessageFilter", new com.vi.agent.core.runtime.context.ModelContextMessageFilter());

        UserMessage userMessage = UserMessage.restore("msg-user", "turn-completed", 1L, "几点了", Instant.now());
        AssistantMessage assistantMessage = AssistantMessage.restore(
            "msg-assistant",
            "turn-completed",
            2L,
            "我来查询时间",
            List.of(ModelToolCall.builder().toolCallId("call-1").toolName("get_time").argumentsJson("{}").build()),
            Instant.now()
        );
        ToolCallMessage toolCallMessage = ToolCallMessage.restore(
            "msg-tool-call",
            "turn-completed",
            3L,
            "call-1",
            "get_time",
            "{}",
            Instant.now()
        );
        ToolResultMessage toolResultMessage = ToolResultMessage.restore(
            "msg-tool-result",
            "turn-completed",
            4L,
            "call-1",
            "get_time",
            true,
            "2026-04-21T16:00:00+08:00",
            null,
            null,
            1L,
            Instant.now()
        );

        sessionStateRepository.existingSnapshot = SessionStateSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .messages(List.of(userMessage, assistantMessage, toolCallMessage, toolResultMessage))
            .updatedAt(Instant.now())
            .build();
        turnRepository.completedTurnIds.add("turn-completed");

        List<Message> loaded = loader.load("conv-1", "sess-1");

        assertEquals(3, loaded.size());
        assertTrue(loaded.stream().noneMatch(message -> message instanceof ToolCallMessage));
        assertTrue(loaded.stream().anyMatch(AssistantMessage.class::isInstance));
        assertTrue(loaded.stream().anyMatch(ToolResultMessage.class::isInstance));

        assertEquals(3, sessionStateRepository.savedSnapshot.getMessages().size());
        assertTrue(sessionStateRepository.savedSnapshot.getMessages().stream()
            .noneMatch(message -> message instanceof ToolCallMessage));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = SessionStateLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CapturingSessionStateRepository implements SessionStateRepository {
        private SessionStateSnapshot existingSnapshot;
        private SessionStateSnapshot savedSnapshot;

        @Override
        public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
            return Optional.ofNullable(existingSnapshot);
        }

        @Override
        public void save(SessionStateSnapshot snapshot) {
            this.savedSnapshot = snapshot;
        }

        @Override
        public void evict(String sessionId) {
            // no-op
        }
    }

    private static final class StubMessageRepository implements MessageRepository {
        private List<Message> messages = List.of();

        @Override
        public void save(String conversationId, String sessionId, Message message) {
            // no-op
        }

        @Override
        public Optional<Message> findByMessageId(String messageId) {
            return messages.stream().filter(message -> message.getMessageId().equals(messageId)).findFirst();
        }

        @Override
        public List<Message> findBySessionIdOrderBySequence(String sessionId, int limit) {
            return new ArrayList<>(messages);
        }

        @Override
        public long nextSequenceNo(String sessionId) {
            return 1L;
        }
    }

    private static final class StubTurnRepository implements TurnRepository {
        private final List<String> completedTurnIds = new ArrayList<>();
        private final List<String> failedTurnIds = new ArrayList<>();

        @Override
        public Optional<Turn> findByRequestId(String requestId) {
            return Optional.empty();
        }

        @Override
        public Optional<Turn> findByTurnId(String turnId) {
            if (completedTurnIds.contains(turnId)) {
                return Optional.of(Turn.builder()
                    .turnId(turnId)
                    .conversationId("conv-1")
                    .sessionId("sess-1")
                    .requestId("req")
                    .runId("run")
                    .status(TurnStatus.COMPLETED)
                    .userMessageId("u")
                    .createdAt(Instant.now())
                    .build());
            }
            if (failedTurnIds.contains(turnId)) {
                return Optional.of(Turn.builder()
                    .turnId(turnId)
                    .conversationId("conv-1")
                    .sessionId("sess-1")
                    .requestId("req")
                    .runId("run")
                    .status(TurnStatus.FAILED)
                    .userMessageId("u")
                    .createdAt(Instant.now())
                    .build());
            }
            return Optional.empty();
        }

        @Override
        public boolean existsRunningBySessionId(String sessionId) {
            return false;
        }

        @Override
        public void save(Turn turn) {
            // no-op
        }

        @Override
        public void update(Turn turn) {
            // no-op
        }
    }
}
