package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateSnapshotDocument;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Session State Redis snapshot mapper。
 */
@Component
public class SessionStateRedisMapper {

    /**
     * 将领域 state 快照转换为 Redis 文档对象。
     *
     * @param snapshot 领域 state 快照
     * @return Redis 文档对象
     */
    public SessionStateSnapshotDocument toDocument(SessionStateSnapshot snapshot) {
        return SessionStateSnapshotDocument.builder()
            .snapshotId(snapshot.getSnapshotId())
            .sessionId(snapshot.getSessionId())
            .stateVersion(snapshot.getStateVersion())
            .taskGoal(snapshot.getTaskGoal())
            .stateJson(JsonUtils.toJson(snapshot))
            .snapshotVersion(1)
            .updatedAtEpochMs(toEpochMillis(snapshot.getUpdatedAt()))
            .build();
    }

    /**
     * 将 Redis 文档对象还原为领域 state 快照。
     *
     * @param document Redis 文档对象
     * @return 领域 state 快照
     */
    public SessionStateSnapshot toModel(SessionStateSnapshotDocument document) {
        return JsonUtils.jsonToBean(document.getStateJson(), SessionStateSnapshot.class);
    }

    /** 将 Instant 转换为毫秒时间戳。 */
    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
