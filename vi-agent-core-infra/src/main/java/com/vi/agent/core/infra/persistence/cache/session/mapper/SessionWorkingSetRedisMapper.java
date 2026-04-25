package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionWorkingSetSnapshotDocument;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Session working set Redis snapshot mapper。
 */
@Component
public class SessionWorkingSetRedisMapper {

    /**
     * 将领域 working set 快照转换为 Redis 文档对象。
     *
     * @param snapshot 领域 working set 快照
     * @return Redis 文档对象
     */
    public SessionWorkingSetSnapshotDocument toDocument(SessionWorkingSetSnapshot snapshot) {
        return SessionWorkingSetSnapshotDocument.builder()
            .sessionId(snapshot.getSessionId())
            .conversationId(snapshot.getConversationId())
            .workingSetVersion(snapshot.getWorkingSetVersion())
            .maxCompletedTurns(snapshot.getMaxCompletedTurns())
            .summaryCoveredToSequenceNo(snapshot.getSummaryCoveredToSequenceNo())
            .rawMessageIdsJson(JsonUtils.toJson(CollectionUtils.isEmpty(snapshot.getRawMessageIds())
                ? List.of()
                : snapshot.getRawMessageIds()))
            .snapshotVersion(1)
            .updatedAtEpochMs(toEpochMillis(snapshot.getUpdatedAt()))
            .build();
    }

    /**
     * 将 Redis 文档对象还原为领域 working set 快照。
     *
     * @param document Redis 文档对象
     * @return 领域 working set 快照
     */
    public SessionWorkingSetSnapshot toModel(SessionWorkingSetSnapshotDocument document) {
        return SessionWorkingSetSnapshot.builder()
            .sessionId(document.getSessionId())
            .conversationId(document.getConversationId())
            .workingSetVersion(document.getWorkingSetVersion())
            .maxCompletedTurns(document.getMaxCompletedTurns())
            .summaryCoveredToSequenceNo(document.getSummaryCoveredToSequenceNo())
            .rawMessageIds(parseRawMessageIds(document.getRawMessageIdsJson()))
            .updatedAt(fromEpochMillis(document.getUpdatedAtEpochMs()))
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

    /** 解析 raw message ID 列表 JSON。 */
    private List<String> parseRawMessageIds(String rawMessageIdsJson) {
        if (StringUtils.isBlank(rawMessageIdsJson)) {
            return List.of();
        }
        List<String> rawMessageIds = JsonUtils.jsonToBean(
            rawMessageIdsJson,
            new TypeReference<List<String>>() {
            }.getType()
        );
        return CollectionUtils.isEmpty(rawMessageIds) ? List.of() : rawMessageIds;
    }
}
