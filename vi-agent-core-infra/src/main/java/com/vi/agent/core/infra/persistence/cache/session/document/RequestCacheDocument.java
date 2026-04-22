package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * requestId 辅助缓存文档。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestCacheDocument {

    private String requestId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String runId;

    private String runStatus;

    private Long createdAtEpochMs;
}
