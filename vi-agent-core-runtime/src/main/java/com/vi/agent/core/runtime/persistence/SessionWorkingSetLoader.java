package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
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
            .orElseGet(() -> refreshFromMysql(conversationId, sessionId).getMessages());
    }

    /**
     * 基于 SQL transcript 重新加载 completed raw messages，并回刷 Redis working set snapshot。
     */
    public SessionWorkingSetSnapshot refreshFromMysql(String conversationId, String sessionId) {
        List<Message> completedMessages = messageRepository.findCompletedContextBySessionId(sessionId, maxCompletedTurns);
        SessionWorkingSetSnapshot currentSnapshot = sessionWorkingSetRepository.findBySessionId(sessionId);
        Long workingSetVersion = Optional.ofNullable(currentSnapshot)
            .map(SessionWorkingSetSnapshot::getWorkingSetVersion)
            .orElse(0L) + 1L;

        SessionWorkingSetSnapshot newSnapshot = SessionWorkingSetSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .workingSetVersion(workingSetVersion)
            .maxCompletedTurns(maxCompletedTurns)
            .summaryCoveredToSequenceNo(resolveSummaryCoveredToSequenceNo(completedMessages))
            .rawMessageIds(completedMessages.stream().map(Message::getMessageId).toList())
            .messages(new ArrayList<>(completedMessages))
            .updatedAt(Instant.now())
            .build();

        sessionWorkingSetRepository.save(newSnapshot);
        return newSnapshot;
    }

    private Long resolveSummaryCoveredToSequenceNo(List<Message> messages) {
        return messages.stream()
            .map(Message::getSequenceNo)
            .max(Long::compareTo)
            .orElse(0L);
    }
}
