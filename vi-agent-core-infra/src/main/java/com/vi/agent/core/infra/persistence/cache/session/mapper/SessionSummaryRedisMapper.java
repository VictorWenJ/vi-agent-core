package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionSummarySnapshotDocument;
import com.vi.agent.core.model.memory.ConversationSummary;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Session Summary Redis snapshot mapper。
 */
@Component
public class SessionSummaryRedisMapper {

    /**
     * 将领域 summary 转换为 Redis 文档对象。
     *
     * @param summary 领域 summary
     * @return Redis 文档对象
     */
    public SessionSummarySnapshotDocument toDocument(ConversationSummary summary) {
        return SessionSummarySnapshotDocument.builder()
            .summaryId(summary.getSummaryId())
            .sessionId(summary.getSessionId())
            .summaryVersion(summary.getSummaryVersion())
            .coveredFromSequenceNo(summary.getCoveredFromSequenceNo())
            .coveredToSequenceNo(summary.getCoveredToSequenceNo())
            .summaryText(summary.getSummaryText())
            .summaryTemplateKey(summary.getSummaryTemplateKey())
            .summaryTemplateVersion(summary.getSummaryTemplateVersion())
            .generatorProvider(summary.getGeneratorProvider())
            .generatorModel(summary.getGeneratorModel())
            .snapshotVersion(1)
            .createdAtEpochMs(toEpochMillis(summary.getCreatedAt()))
            .build();
    }

    /**
     * 将 Redis 文档对象还原为领域 summary。
     *
     * @param document Redis 文档对象
     * @return 领域 summary
     */
    public ConversationSummary toModel(SessionSummarySnapshotDocument document) {
        return ConversationSummary.builder()
            .summaryId(document.getSummaryId())
            .sessionId(document.getSessionId())
            .summaryVersion(document.getSummaryVersion())
            .coveredFromSequenceNo(document.getCoveredFromSequenceNo())
            .coveredToSequenceNo(document.getCoveredToSequenceNo())
            .summaryText(document.getSummaryText())
            .summaryTemplateKey(document.getSummaryTemplateKey())
            .summaryTemplateVersion(document.getSummaryTemplateVersion())
            .generatorProvider(document.getGeneratorProvider())
            .generatorModel(document.getGeneratorModel())
            .createdAt(fromEpochMillis(document.getCreatedAtEpochMs()))
            .build();
    }

    /** 将 Instant 转换为毫秒时间戳。 */
    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    /** 将毫秒时间戳转换为 Instant。 */
    private Instant fromEpochMillis(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }
}
