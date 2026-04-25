package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Session working set loader.
 */
@Slf4j
@Component
public class SessionWorkingSetLoader {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private SessionWorkingSetRepository sessionWorkingSetRepository;

    @Value("${vi.agent.runtime.session-working-set.max-completed-turns:5}")
    private int maxCompletedTurns;

    public List<Message> load(String conversationId, String sessionId) {
        return refreshFromMysql(conversationId, sessionId);
    }

    /**
     * 基于 SQL transcript 重新加载 completed raw messages，并回刷 Redis working set snapshot。
     */
    public List<Message> refreshFromMysql(String conversationId, String sessionId) {
        List<Message> completedMessages = messageRepository.findCompletedContextBySessionId(sessionId, maxCompletedTurns);

        // 获取最新work set快照数据版本
        Optional<SessionWorkingSetSnapshot> oldSessionWorkingSetSnapshot = sessionWorkingSetRepository.findBySessionId(sessionId);
        log.info("SessionWorkingSetLoader refreshFromMysql oldSessionWorkingSetSnapshot:{}", JsonUtils.toJson(oldSessionWorkingSetSnapshot.orElse(null)));

        Long workingSetVersion = oldSessionWorkingSetSnapshot
            .map(SessionWorkingSetSnapshot::getWorkingSetVersion)
            .orElse(0L) + 1L;


        SessionWorkingSetSnapshot newSessionWorkingSetSnapshot = SessionWorkingSetSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .workingSetVersion(workingSetVersion)
            .maxCompletedTurns(maxCompletedTurns)
            .summaryCoveredToSequenceNo(resolveSummaryCoveredToSequenceNo(completedMessages))
            .rawMessageIds(completedMessages.stream().map(Message::getMessageId).toList())
            .updatedAt(Instant.now())
            .build();
        log.info("SessionWorkingSetLoader refreshFromMysql newSessionWorkingSetSnapshot:{}", JsonUtils.toJson(newSessionWorkingSetSnapshot));

        // 回写最新版本work set快照数据
        sessionWorkingSetRepository.save(newSessionWorkingSetSnapshot);
        return new ArrayList<>(completedMessages);
    }

    private Long resolveSummaryCoveredToSequenceNo(List<Message> messages) {
        return messages.stream()
            .map(Message::getSequenceNo)
            .max(Long::compareTo)
            .orElse(0L);
    }
}
