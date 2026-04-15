package com.vi.agent.core.infra.persistence;

import java.util.Optional;

/**
 * Transcript 仓储接口。
 */
public interface TranscriptRepository {

    /**
     * 读取会话 Transcript。
     *
     * @param sessionId 会话 ID
     * @return 实体对象
     */
    Optional<ConversationTranscriptEntity> findBySessionId(String sessionId);

    /**
     * 保存会话 Transcript。
     *
     * @param entity 持久化实体
     */
    void save(ConversationTranscriptEntity entity);
}
