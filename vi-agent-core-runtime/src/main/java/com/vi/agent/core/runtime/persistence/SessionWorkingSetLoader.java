package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Session working set loader.
 */
@Component
public class SessionWorkingSetLoader {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private SessionWorkingSetRepository sessionWorkingSetRepository;

    @Value("${vi.agent.runtime.session-working-set.max-completed-turns:5}")
    private int maxCompletedTurns;

    public List<Message> load(String conversationId, String sessionId) {
        return Optional.ofNullable(sessionWorkingSetRepository.findBySessionId(sessionId))
            .map(SessionWorkingSetSnapshot::getMessages)
            .orElseGet(() -> reloadFromMysql(conversationId, sessionId));
    }

    private List<Message> reloadFromMysql(String conversationId, String sessionId) {
        List<Message> completedMessages = messageRepository.findCompletedContextBySessionId(sessionId, maxCompletedTurns);
        sessionWorkingSetRepository.save(SessionWorkingSetSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .maxCompletedTurns(maxCompletedTurns)
            .rawMessageIds(completedMessages.stream().map(Message::getMessageId).toList())
            .messages(new ArrayList<>(completedMessages))
            .updatedAt(Instant.now())
            .build());
        return completedMessages;
    }
}

