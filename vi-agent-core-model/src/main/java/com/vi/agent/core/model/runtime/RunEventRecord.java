package com.vi.agent.core.model.runtime;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Run 事件事实记录。
 */
@Getter
@Builder
public class RunEventRecord {

    private final String eventId;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final Integer eventIndex;

    private final RunEventType eventType;

    private final String actorType;

    private final String actorId;

    private final String payloadJson;

    private final Instant createdAt;
}
