package com.vi.agent.core.runtime.state;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.context.ModelContextMessageFilter;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads session working state from redis cache or mysql fact source.
 */
@Service
public class SessionStateLoader {

    @Resource
    private SessionStateRepository sessionStateRepository;

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private TurnRepository turnRepository;

    @Resource
    private ModelContextMessageFilter modelContextMessageFilter;

    @Value("${vi.agent.runtime.session-state-window:200}")
    private int maxWindow;

    public List<Message> load(String conversationId, String sessionId) {
        List<Message> messages = sessionStateRepository.findBySessionId(sessionId)
            .map(SessionStateSnapshot::getMessages)
            .orElseGet(() -> reloadFromMysql(conversationId, sessionId));
        List<Message> completedTurnMessages = filterCompletedTurnMessages(messages);
        List<Message> modelContextMessages = modelContextMessageFilter.filter(completedTurnMessages);
        if (modelContextMessages.size() != messages.size()) {
            refresh(conversationId, sessionId, modelContextMessages);
        }
        return new ArrayList<>(modelContextMessages);
    }

    public void refresh(String conversationId, String sessionId, List<Message> messages) {
        List<Message> modelContextMessages = modelContextMessageFilter.filter(messages);
        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .messages(new ArrayList<>(modelContextMessages))
            .updatedAt(Instant.now())
            .build());
    }

    private List<Message> reloadFromMysql(String conversationId, String sessionId) {
        List<Message> messages = messageRepository.findBySessionIdOrderBySequence(sessionId, maxWindow);
        List<Message> completedTurnMessages = filterCompletedTurnMessages(messages);
        List<Message> modelContextMessages = modelContextMessageFilter.filter(completedTurnMessages);
        refresh(conversationId, sessionId, modelContextMessages);
        return modelContextMessages;
    }

    private List<Message> filterCompletedTurnMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Boolean> completedTurnCache = new HashMap<>();
        return messages.stream()
            .filter(message -> completedTurnCache.computeIfAbsent(message.getTurnId(), this::isCompletedTurn))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private boolean isCompletedTurn(String turnId) {
        if (turnId == null || turnId.isBlank()) {
            return false;
        }
        return turnRepository.findByTurnId(turnId)
            .map(turn -> turn.getStatus() == TurnStatus.COMPLETED)
            .orElse(false);
    }
}
