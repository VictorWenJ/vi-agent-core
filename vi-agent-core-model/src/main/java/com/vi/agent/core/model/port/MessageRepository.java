package com.vi.agent.core.model.port;

import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolExecution;

import java.util.List;

/**
 * Message persistence port.
 */
public interface MessageRepository {

    void saveBatch(List<Message> messages);

    default void save(Message message) {
        saveBatch(List.of(message));
    }

    Message findByMessageId(String messageId);

    List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns);

    List<Message> findByTurnId(String turnId);

    Message findFinalAssistantMessageByTurnId(String turnId);

    long nextSequenceNo(String sessionId);

    default void saveFailureToolFacts(List<AssistantToolCall> toolCalls, List<ToolExecution> toolExecutions) {
        // no-op by default
    }
}
