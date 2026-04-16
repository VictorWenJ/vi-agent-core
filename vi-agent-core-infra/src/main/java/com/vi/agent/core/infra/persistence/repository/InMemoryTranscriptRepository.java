package com.vi.agent.core.infra.persistence.repository;

import com.vi.agent.core.infra.persistence.config.RedisTranscriptProperties;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Transcript 仓储（测试/占位）。
 */
public class InMemoryTranscriptRepository implements TranscriptRepository {

    /** 会话缓存。 */
    private final Map<String, RedisTranscriptProperties.ConversationTranscriptEntity> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<RedisTranscriptProperties.ConversationTranscriptEntity> findBySessionId(String sessionId) {
        return Optional.ofNullable(storage.get(sessionId));
    }

    @Override
    public void save(RedisTranscriptProperties.ConversationTranscriptEntity entity) {
        storage.put(entity.getSessionId(), entity);
    }
}
