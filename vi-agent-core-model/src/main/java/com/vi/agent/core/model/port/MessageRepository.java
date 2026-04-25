package com.vi.agent.core.model.port;

import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageStatus;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Message persistence port.
 */
public interface MessageRepository {

    void saveBatch(List<Message> messages);

    default void save(Message message) {
        saveBatch(List.of(message));
    }

    Optional<Message> findByMessageId(String messageId);

    List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns);

    List<Message> findByTurnId(String turnId);

    default List<Message> findCompletedMessagesByTurnId(String turnId) {
        List<Message> messages = findByTurnId(turnId);
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
            .filter(Objects::nonNull)
            .filter(message -> message.getStatus() == MessageStatus.COMPLETED)
            .toList();
    }

    Optional<Message> findFinalAssistantMessageByTurnId(String turnId);

    long nextSequenceNo(String sessionId);

    default void saveAssistantMessageIfAbsent(AssistantMessage assistantMessage) {
        if (assistantMessage != null) {
            save(assistantMessage);
        }
    }

    default void saveToolCallCreated(AssistantToolCall toolCall) {
        // no-op by default
    }

    default void updateToolCallStatus(String toolCallRecordId, ToolCallStatus status) {
        // no-op by default
    }

    default void upsertToolExecutionRunning(ToolExecution toolExecution) {
        // no-op by default
    }

    default void updateToolExecutionFinal(ToolExecution toolExecution) {
        // no-op by default
    }

    default void saveFailureToolFacts(List<AssistantToolCall> toolCalls, List<ToolExecution> toolExecutions) {
        // no-op by default
    }
}
