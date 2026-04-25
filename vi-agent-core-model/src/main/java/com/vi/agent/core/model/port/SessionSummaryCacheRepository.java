package com.vi.agent.core.model.port;

import com.vi.agent.core.model.memory.ConversationSummary;

import java.util.Optional;

/**
 * Session Summary snapshot cache 仓储端口。
 */
public interface SessionSummaryCacheRepository {

    /** 按 session ID 读取缓存中的 summary snapshot。 */
    Optional<ConversationSummary> findBySessionId(String sessionId);

    /** 写入缓存中的 summary snapshot。 */
    void save(ConversationSummary summary);

    /** 删除缓存中的 summary snapshot。 */
    void evict(String sessionId);
}
