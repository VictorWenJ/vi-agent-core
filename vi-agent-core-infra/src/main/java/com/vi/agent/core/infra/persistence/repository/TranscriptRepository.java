package com.vi.agent.core.infra.persistence.repository;

import com.vi.agent.core.infra.persistence.entity.RedisTranscriptEntity;

import java.util.Optional;

/**
 * Transcript 仓储接口。
 */
public interface TranscriptRepository {

    /**
     * 读取会话 Transcript。
     *
     * @param sessionId 会话 ID
     * @return 持久化实体
     */
    Optional<RedisTranscriptEntity> findBySessionId(String sessionId);

    /**
     * 保存会话 Transcript。
     *
     * @param entity 持久化实体
     */
    void save(RedisTranscriptEntity entity);
}
