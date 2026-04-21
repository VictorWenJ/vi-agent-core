package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Session state cache document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStateCacheDocument {

    private String sessionId;

    private String conversationId;

    private List<SessionStateMessageDocument> messageDocuments;

    private String transcriptCache;

    private String recentWindowCache;

    private String summaryCheckpoint;

    private Instant updatedAt;
}
