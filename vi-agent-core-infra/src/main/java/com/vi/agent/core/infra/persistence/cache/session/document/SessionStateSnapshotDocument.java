package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session State Redis snapshot cache 文档对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStateSnapshotDocument {

    /** state 快照 ID。 */
    private String snapshotId;

    /** session ID。 */
    private String sessionId;

    /** state 版本号。 */
    private Long stateVersion;

    /** 当前任务目标冗余字段。 */
    private String taskGoal;

    /** 完整 state 快照 JSON。 */
    private String stateJson;

    /** snapshot cache 格式版本。 */
    private Integer snapshotVersion;

    /** 更新时间毫秒时间戳。 */
    private Long updatedAtEpochMs;
}
