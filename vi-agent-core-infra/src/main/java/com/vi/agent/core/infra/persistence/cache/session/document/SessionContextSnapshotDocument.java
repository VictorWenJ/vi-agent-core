package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session 上下文快照文档。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContextSnapshotDocument {

    private String sessionId;

    private String conversationId;

    private Long fromSequenceNo;

    private Long toSequenceNo;

    private Integer messageCount;

    private Integer snapshotVersion;

    private String messagesJson;

    private Long updatedAtEpochMs;
}
