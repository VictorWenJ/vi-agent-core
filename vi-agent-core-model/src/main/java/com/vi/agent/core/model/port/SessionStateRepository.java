package com.vi.agent.core.model.port;

import com.vi.agent.core.model.memory.SessionStateSnapshot;

import java.util.Optional;

/**
 * Session State 事实源仓储端口。
 */
public interface SessionStateRepository {

    /**
     * 保存 session state 快照。
     *
     * @param snapshot session state 快照
     */
    void save(SessionStateSnapshot snapshot);

    /**
     * 按快照 ID 查询 state。
     *
     * @param snapshotId state 快照 ID
     * @return state 快照
     */
    Optional<SessionStateSnapshot> findBySnapshotId(String snapshotId);

    /**
     * 查询 session 下最新 state。
     *
     * @param sessionId session ID
     * @return 最新 state 快照
     */
    Optional<SessionStateSnapshot> findLatestBySessionId(String sessionId);

    /**
     * 按 session 与版本查询 state。
     *
     * @param sessionId session ID
     * @param stateVersion state 版本号
     * @return state 快照
     */
    Optional<SessionStateSnapshot> findBySessionIdAndStateVersion(String sessionId, Long stateVersion);
}
