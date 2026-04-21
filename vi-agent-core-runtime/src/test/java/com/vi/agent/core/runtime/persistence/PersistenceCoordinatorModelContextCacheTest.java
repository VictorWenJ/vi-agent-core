package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.ConversationRepository;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.ToolExecutionRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.context.ModelContextMessageFilter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceCoordinatorModelContextCacheTest {

    @Test
    void persistSuccessShouldStoreToolCallFactButExcludeToolCallFromSessionState() throws Exception {
        PersistenceCoordinator coordinator = new PersistenceCoordinator();
        CapturingMessageRepository messageRepository = new CapturingMessageRepository();
        CapturingToolExecutionRepository toolExecutionRepository = new CapturingToolExecutionRepository();
        CapturingTurnRepository turnRepository = new CapturingTurnRepository();
        CapturingSessionRepository sessionRepository = new CapturingSessionRepository();
        CapturingConversationRepository conversationRepository = new CapturingConversationRepository();
        CapturingSessionStateRepository sessionStateRepository = new CapturingSessionStateRepository();

        setField(coordinator, "messageRepository", messageRepository);
        setField(coordinator, "toolExecutionRepository", toolExecutionRepository);
        setField(coordinator, "turnRepository", turnRepository);
        setField(coordinator, "sessionRepository", sessionRepository);
        setField(coordinator, "conversationRepository", conversationRepository);
        setField(coordinator, "sessionStateRepository", sessionStateRepository);
        setField(coordinator, "modelContextMessageFilter", new ModelContextMessageFilter());

        Conversation conversation = Conversation.builder()
            .conversationId("conv-1")
            .title("test")
            .status(ConversationStatus.ACTIVE)
            .activeSessionId("sess-1")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .lastMessageAt(Instant.now())
            .build();
        Session session = Session.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .status(SessionStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        Turn turn = Turn.builder()
            .turnId("turn-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .requestId("req-1")
            .runId("run-1")
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-user")
            .createdAt(Instant.now())
            .build();

        UserMessage userMessage = UserMessage.create("msg-user", "turn-1", 1L, "现在几点");
        AssistantMessage assistantWithToolCall = AssistantMessage.create("msg-assistant-tool", "turn-1", 2L, "我来查询", List.of());
        ToolCallMessage toolCallMessage = ToolCallMessage.create("msg-tool-call", "turn-1", 3L, "call-1", "get_time", "{}");
        ToolResultMessage toolResultMessage = ToolResultMessage.create(
            "msg-tool-result",
            "turn-1",
            4L,
            "call-1",
            "get_time",
            true,
            "2026-04-21T16:00:00+08:00",
            null,
            null,
            1L
        );
        AssistantMessage finalAssistant = AssistantMessage.create("msg-assistant-final", "turn-1", 5L, "现在是 16:00", List.of());

        AgentRunContext runContext = AgentRunContext.builder()
            .runMetadata(RunMetadata.builder().traceId("trace-1").runId("run-1").turnId("turn-1").build())
            .conversation(conversation)
            .session(session)
            .turn(turn)
            .userInput("现在几点")
            .workingMessages(new ArrayList<>(List.of(userMessage, assistantWithToolCall, toolCallMessage, toolResultMessage, finalAssistant)))
            .availableTools(List.of())
            .build();

        LoopExecutionResult loopExecutionResult = LoopExecutionResult.builder()
            .assistantMessage(finalAssistant)
            .appendedMessages(List.of(assistantWithToolCall, toolCallMessage, toolResultMessage, finalAssistant))
            .toolCalls(List.of(ToolCallRecord.builder()
                .toolCallId("call-1")
                .conversationId("conv-1")
                .sessionId("sess-1")
                .turnId("turn-1")
                .messageId("msg-tool-call")
                .toolName("get_time")
                .argumentsJson("{}")
                .sequenceNo(1)
                .status("REQUESTED")
                .createdAt(Instant.now())
                .build()))
            .toolResults(List.of(ToolResultRecord.builder()
                .toolCallId("call-1")
                .conversationId("conv-1")
                .sessionId("sess-1")
                .turnId("turn-1")
                .messageId("msg-tool-result")
                .toolName("get_time")
                .success(true)
                .outputJson("2026-04-21T16:00:00+08:00")
                .durationMs(1L)
                .createdAt(Instant.now())
                .build()))
            .finishReason(FinishReason.STOP)
            .usage(UsageInfo.empty())
            .build();

        coordinator.persistSuccess(runContext, loopExecutionResult);

        assertTrue(messageRepository.savedMessages.stream().anyMatch(ToolCallMessage.class::isInstance));
        assertTrue(toolExecutionRepository.savedToolCalls.stream()
            .anyMatch(record -> "call-1".equals(record.getToolCallId())));
        assertTrue(toolExecutionRepository.savedToolResults.stream()
            .anyMatch(record -> "call-1".equals(record.getToolCallId())));
        assertTrue(sessionStateRepository.savedSnapshot.getMessages().stream()
            .noneMatch(ToolCallMessage.class::isInstance));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = PersistenceCoordinator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CapturingMessageRepository implements MessageRepository {
        private final List<Message> savedMessages = new ArrayList<>();

        @Override
        public void save(String conversationId, String sessionId, Message message) {
            savedMessages.add(message);
        }

        @Override
        public Optional<Message> findByMessageId(String messageId) {
            return Optional.empty();
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

    private static final class CapturingToolExecutionRepository implements ToolExecutionRepository {
        private final List<ToolCallRecord> savedToolCalls = new ArrayList<>();
        private final List<ToolResultRecord> savedToolResults = new ArrayList<>();

        @Override
        public void saveToolCall(ToolCallRecord toolCallRecord) {
            savedToolCalls.add(toolCallRecord);
        }

        @Override
        public void saveToolResult(ToolResultRecord toolResultRecord) {
            savedToolResults.add(toolResultRecord);
        }
    }

    private static final class CapturingTurnRepository implements TurnRepository {
        @Override
        public Optional<Turn> findByRequestId(String requestId) {
            return Optional.empty();
        }

        @Override
        public Optional<Turn> findByTurnId(String turnId) {
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

    private static final class CapturingSessionRepository implements SessionRepository {
        @Override
        public Optional<Session> findBySessionId(String sessionId) {
            return Optional.empty();
        }

        @Override
        public Optional<Session> findActiveByConversationId(String conversationId) {
            return Optional.empty();
        }

        @Override
        public void save(Session session) {
            // no-op
        }

        @Override
        public void update(Session session) {
            // no-op
        }
    }

    private static final class CapturingConversationRepository implements ConversationRepository {
        @Override
        public Optional<Conversation> findByConversationId(String conversationId) {
            return Optional.empty();
        }

        @Override
        public void save(Conversation conversation) {
            // no-op
        }

        @Override
        public void update(Conversation conversation) {
            // no-op
        }
    }

    private static final class CapturingSessionStateRepository implements SessionStateRepository {
        private SessionStateSnapshot savedSnapshot;

        @Override
        public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
            return Optional.empty();
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
}

