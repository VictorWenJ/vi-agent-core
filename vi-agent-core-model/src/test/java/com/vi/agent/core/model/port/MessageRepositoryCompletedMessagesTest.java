package com.vi.agent.core.model.port;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageStatus;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageRepositoryCompletedMessagesTest {

    @Test
    void findCompletedMessagesByTurnIdShouldOnlyReturnCompletedTurnMessages() {
        MessageRepository repository = new StubMessageRepository();

        List<Message> messages = repository.findCompletedMessagesByTurnId("turn-1");

        assertEquals(List.of("msg-user-1", "msg-assistant-1"), messages.stream().map(Message::getMessageId).toList());
    }

    private static final class StubMessageRepository implements MessageRepository {
        @Override
        public void saveBatch(List<Message> messages) {
        }

        @Override
        public Optional<Message> findByMessageId(String messageId) {
            return Optional.empty();
        }

        @Override
        public List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns) {
            return List.of();
        }

        @Override
        public List<Message> findByTurnId(String turnId) {
            return List.of(
                UserMessage.create("msg-user-1", "conv-1", "sess-1", turnId, "run-1", 1L, "remember this"),
                AssistantMessage.create("msg-assistant-1", "conv-1", "sess-1", turnId, "run-1", 2L, "ok", List.of(), null, null),
                UserMessage.restore("msg-running", "conv-1", "sess-1", turnId, "run-1", 3L, MessageStatus.RUNNING, "running", Instant.now())
            );
        }

        @Override
        public Optional<Message> findFinalAssistantMessageByTurnId(String turnId) {
            return Optional.empty();
        }

        @Override
        public long nextSequenceNo(String sessionId) {
            return 1L;
        }

        @Override
        public void updateToolCallStatus(String toolCallRecordId, ToolCallStatus status) {
        }

        @Override
        public void upsertToolExecutionRunning(ToolExecution toolExecution) {
        }

        @Override
        public void updateToolExecutionFinal(ToolExecution toolExecution) {
        }
    }
}
