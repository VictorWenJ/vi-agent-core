package com.vi.agent.core.runtime.state;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads session working state from redis cache or mysql fact source.
 */
@Service
public class SessionStateLoader {

    @Resource
    private SessionStateRepository sessionStateRepository;

    @Resource
    private MessageRepository messageRepository;

    @Value("${vi.agent.runtime.session-state-window:200}")
    private int maxWindow;

    public List<Message> load(String conversationId, String sessionId) {
        return sessionStateRepository.findBySessionId(sessionId)
            .map(SessionStateSnapshot::getMessages)
            .orElseGet(() -> reloadFromMysql(conversationId, sessionId));
    }

    public void refresh(String conversationId, String sessionId, List<Message> messages) {
        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .messages(new ArrayList<>(messages))
            .updatedAt(Instant.now())
            .build());
    }

    private List<Message> reloadFromMysql(String conversationId, String sessionId) {
        List<Message> messages = messageRepository.findBySessionIdOrderBySequence(sessionId, maxWindow);
        refresh(conversationId, sessionId, messages);
        return messages;
    }
}
