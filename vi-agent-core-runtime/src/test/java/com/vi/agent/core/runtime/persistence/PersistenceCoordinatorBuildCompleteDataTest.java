package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.ToolExecutionRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceCoordinatorBuildCompleteDataTest {

    @Test
    void loadShouldBuildCompleteMessageDataByMessageType() {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        StubSessionStateRepository sessionStateRepository = new StubSessionStateRepository();
        StubTurnRepository turnRepository = new StubTurnRepository();
        StubToolExecutionRepository toolExecutionRepository = new StubToolExecutionRepository();
        StubMessageRepository messageRepository = new StubMessageRepository();

        TestFieldUtils.setField(coordinator, "sessionStateRepository", sessionStateRepository);
        TestFieldUtils.setField(coordinator, "turnRepository", turnRepository);
        TestFieldUtils.setField(coordinator, "toolExecutionRepository", toolExecutionRepository);
        TestFieldUtils.setField(coordinator, "messageRepository", messageRepository);
        TestFieldUtils.setField(coordinator, "maxWindow", 200);

        List<Message> messages = coordinator.load("conv-1", "sess-1");
        assertEquals(3, messages.size());

        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(0));
        AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, messages.get(1));
        ToolResultMessage toolResultMessage = assertInstanceOf(ToolResultMessage.class, messages.get(2));

        assertEquals("hello", userMessage.getContent());
        assertEquals(1, assistantMessage.getToolCalls().size());
        ModelToolCall modelToolCall = assistantMessage.getToolCalls().get(0);
        assertEquals("call-1", modelToolCall.getToolCallId());
        assertEquals("get_time", modelToolCall.getToolName());
        assertEquals("{}", modelToolCall.getArgumentsJson());

        assertEquals("call-1", toolResultMessage.getToolCallId());
        assertEquals("get_time", toolResultMessage.getToolName());
        assertTrue(toolResultMessage.isSuccess());
        assertEquals("2026-04-21T15:47:27+08:00", toolResultMessage.getContent());
        assertEquals(1L, toolResultMessage.getDurationMs());
    }

    private static final class StubSessionStateRepository implements SessionStateRepository {

        @Override
        public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
            List<Message> messages = List.of(
                UserMessage.restore("msg-user-1", "turn-1", 1L, "hello", Instant.now()),
                AssistantMessage.restore("msg-assistant-1", "turn-1", 2L, "我来查时间", List.of(), Instant.now()),
                ToolResultMessage.restore(
                    "msg-tool-result-1",
                    "turn-1",
                    4L,
                    "msg-tool-result-1",
                    "tool",
                    true,
                    "2026-04-21T15:47:27+08:00",
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
            return Optional.of(SessionStateSnapshot.builder()
                .sessionId("sess-1")
                .conversationId("conv-1")
                .messages(messages)
                .updatedAt(Instant.now())
                .build());
        }

        @Override
        public void save(SessionStateSnapshot snapshot) {
        }

        @Override
        public void evict(String sessionId) {
        }
    }

    private static final class StubTurnRepository implements TurnRepository {

        @Override
        public Turn findByRequestId(String requestId) {
            return null;
        }

        @Override
        public Turn findByTurnId(String turnId) {
            return Turn.builder()
                .turnId(turnId)
                .conversationId("conv-1")
                .sessionId("sess-1")
                .requestId("req-1")
                .runId("run-1")
                .status(TurnStatus.COMPLETED)
                .userMessageId("msg-user-1")
                .createdAt(Instant.now())
                .build();
        }

        @Override
        public boolean existsRunningBySessionId(String sessionId) {
            return false;
        }

        @Override
        public void save(Turn turn) {
        }

        @Override
        public void update(Turn turn) {
        }
    }

    private static final class StubToolExecutionRepository implements ToolExecutionRepository {

        @Override
        public void saveToolCall(ToolCallRecord toolCallRecord) {
        }

        @Override
        public void saveToolResult(ToolResultRecord toolResultRecord) {
        }

        @Override
        public ToolCallRecord findToolCallByMessageId(String messageId) {
            return ToolCallRecord.builder()
                .toolCallId("call-1")
                .conversationId("conv-1")
                .sessionId("sess-1")
                .turnId("turn-1")
                .messageId("msg-tool-call-1")
                .toolName("get_time")
                .argumentsJson("{}")
                .sequenceNo(1)
                .status("REQUESTED")
                .createdAt(Instant.now())
                .build();
        }

        @Override
        public ToolResultRecord findToolResultByMessageId(String messageId) {
            return ToolResultRecord.builder()
                .toolCallId("call-1")
                .conversationId("conv-1")
                .sessionId("sess-1")
                .turnId("turn-1")
                .messageId("msg-tool-result-1")
                .toolName("get_time")
                .success(true)
                .outputJson("2026-04-21T15:47:27+08:00")
                .errorCode(null)
                .errorMessage(null)
                .durationMs(1L)
                .createdAt(Instant.now())
                .build();
        }

        @Override
        public List<ToolCallRecord> findToolCallsByTurnId(String turnId) {
            return List.of(
                ToolCallRecord.builder()
                    .toolCallId("call-1")
                    .conversationId("conv-1")
                    .sessionId("sess-1")
                    .turnId("turn-1")
                    .messageId("msg-tool-call-1")
                    .toolName("get_time")
                    .argumentsJson("{}")
                    .sequenceNo(1)
                    .status("REQUESTED")
                    .createdAt(Instant.now())
                    .build()
            );
        }
    }

    private static final class StubMessageRepository implements MessageRepository {

        @Override
        public void save(String conversationId, String sessionId, Message message) {
        }

        @Override
        public Message findByMessageId(String messageId) {
            return ToolCallMessage.restore(
                "msg-tool-call-1",
                "turn-1",
                3L,
                "call-1",
                "get_time",
                "{}",
                Instant.now()
            );
        }

        @Override
        public List<Message> findBySessionIdOrderBySequence(String sessionId, int limit) {
            return List.of();
        }

        @Override
        public long nextSequenceNo(String sessionId) {
            return 1L;
        }
    }
}
