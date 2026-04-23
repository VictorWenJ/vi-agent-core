package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Session context loader.
 */
@Component
public class SessionStateLoader {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private SessionStateRepository sessionStateRepository;

    @Value("${vi.agent.runtime.session-context.max-turns:5}")
    private int maxTurns;

    public List<Message> load(String conversationId, String sessionId) {
        return Optional.ofNullable(sessionStateRepository.findBySessionId(sessionId))
            .map(SessionStateSnapshot::getMessages)
            .orElseGet(() -> reloadFromMysql(conversationId, sessionId));
    }

    private List<Message> reloadFromMysql(String conversationId, String sessionId) {
        List<Message> completedMessages = messageRepository.findCompletedContextBySessionId(sessionId, maxTurns);
        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .messages(new ArrayList<>(completedMessages))
            .updatedAt(Instant.now())
            .build());
        return completedMessages;
    }
}
