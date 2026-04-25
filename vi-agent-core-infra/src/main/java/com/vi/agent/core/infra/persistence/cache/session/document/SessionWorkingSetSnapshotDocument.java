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

    /** 当前 session ID。 */
    private String sessionId;

    /** 会话所属 conversation ID。 */
    private String conversationId;

    /** working set 起始消息序号。 */
    private Long fromSequenceNo;

    /** working set 结束消息序号。 */
    private Long toSequenceNo;

    /** working set 消息数量。 */
    private Integer messageCount;

    /** Redis snapshot DTO 版本。 */
    private Integer snapshotVersion;

    /** 序列化后的 raw message 快照 JSON。 */
    private String messagesJson;

    /** 更新时间 epoch millis。 */
    private Long updatedAtEpochMs;
}