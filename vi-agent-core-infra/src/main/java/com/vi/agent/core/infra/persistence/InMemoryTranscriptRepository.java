package com.vi.agent.core.infra.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 内存版 Transcript 仓储。
 */
public class InMemoryTranscriptRepository implements TranscriptRepository {

    /** 会话缓存。 */
    private final Map<String, ConversationTranscriptEntity> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<ConversationTranscriptEntity> findBySessionId(String sessionId) {
        return Optional.ofNullable(storage.get(sessionId));
    }

    @Override
    public void save(ConversationTranscriptEntity entity) {
        storage.put(entity.getSessionId(), entity);
    }
}
