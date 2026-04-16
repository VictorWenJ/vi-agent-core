package com.vi.agent.core.infra.persistence.repository;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.entity.TranscriptEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Transcript 仓储（测试/占位）。
 */
@Slf4j
public class InMemoryTranscriptRepository implements TranscriptRepository {

    /** 会话缓存。 */
    private final Map<String, TranscriptEntity> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<TranscriptEntity> findBySessionId(String sessionId) {
        TranscriptEntity entity = storage.get(sessionId);
        log.info("InMemoryTranscriptRepository findBySessionId finished sessionId={} entity={}", sessionId, JsonUtils.toJson(entity));
        return Optional.ofNullable(entity);
    }

    @Override
    public void save(TranscriptEntity entity) {
        String sessionId = entity.getSessionId();
        storage.put(sessionId, entity);
        log.info("InMemoryTranscriptRepository save finished sessionId={} entity={}", sessionId, JsonUtils.toJson(entity));
    }
}
