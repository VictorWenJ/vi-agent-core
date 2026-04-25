package com.vi.agent.core.model.port;

import com.vi.agent.core.model.context.WorkingContextSnapshotRecord;

import java.util.List;
import java.util.Optional;

/**
 * WorkingContext 审计快照仓储端口。
 */
public interface WorkingContextSnapshotRepository {

    /** 保存 WorkingContext 审计快照。 */
    void save(WorkingContextSnapshotRecord snapshot);

    /** 按快照 ID 查询 WorkingContext 审计快照。 */
    Optional<WorkingContextSnapshotRecord> findBySnapshotId(String workingContextSnapshotId);

    /** 查询 session 下最新 WorkingContext 审计快照。 */
    Optional<WorkingContextSnapshotRecord> findLatestBySessionId(String sessionId);

    /** 按 run ID 查询 WorkingContext 审计快照列表。 */
    List<WorkingContextSnapshotRecord> listByRunId(String runId);
}
