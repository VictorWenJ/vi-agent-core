package com.vi.agent.core.infra.persistence.repository;

import com.vi.agent.core.infra.persistence.entity.RedisTranscriptEntity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Transcript 仓储（测试/占位）。
 */
public class InMemoryTranscriptRepository implements TranscriptRepository {

    /** 会话缓存。 */
    private final Map<String, RedisTranscriptEntity> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<RedisTranscriptEntity> findBySessionId(String sessionId) {
        return Optional.ofNullable(storage.get(sessionId));
    }

    @Override
    public void save(RedisTranscriptEntity entity) {
        storage.put(entity.getSessionId(), entity);
    }
}
