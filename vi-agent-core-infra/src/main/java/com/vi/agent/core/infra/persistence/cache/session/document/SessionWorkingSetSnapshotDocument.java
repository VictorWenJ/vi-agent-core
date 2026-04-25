package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session working set Redis 快照文档。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionWorkingSetSnapshotDocument {

    /** 会话 ID。 */
    private String sessionId;

    /** 会话关联 conversation ID。 */
    private String conversationId;

    /** working set 版本号。 */
    private Long workingSetVersion;

    /** 当前配置的 completed turn 回溯上限。 */
    private Integer maxCompletedTurns;

    /** summary 覆盖到的 sequenceNo。 */
    private Long summaryCoveredToSequenceNo;

    /** raw message id 列表 JSON。 */
    private String rawMessageIdsJson;

    /** 快照版本。 */
    private Integer snapshotVersion;

    /** 更新时间毫秒。 */
    private Long updatedAtEpochMs;
}
