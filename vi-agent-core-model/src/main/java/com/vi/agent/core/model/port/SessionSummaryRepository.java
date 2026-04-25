package com.vi.agent.core.model.port;

import com.vi.agent.core.model.memory.ConversationSummary;

import java.util.Optional;

/**
 * Session Summary 事实源仓储端口。
 */
public interface SessionSummaryRepository {

    /** 保存 summary 快照。 */
    void save(ConversationSummary summary);

    /** 按 summary ID 查询 summary。 */
    Optional<ConversationSummary> findBySummaryId(String summaryId);

    /** 查询 session 下最新 summary。 */
    Optional<ConversationSummary> findLatestBySessionId(String sessionId);

    /** 按 session 与版本查询 summary。 */
    Optional<ConversationSummary> findBySessionIdAndSummaryVersion(String sessionId, Long summaryVersion);
}
